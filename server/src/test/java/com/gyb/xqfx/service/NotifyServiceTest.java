package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.NotifyMessage;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.NotifyMessageMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 站内通知服务测试
 */
@DisplayName("通知服务测试")
class NotifyServiceTest {

    private NotifyMessageMapper notifyMapper;
    private WarningRecordMapper warningMapper;
    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private NotifyService service;
    private StudentMapper studentMapper;

    @BeforeEach
    void setUp() {
        notifyMapper = Mockito.mock(NotifyMessageMapper.class);
        warningMapper = Mockito.mock(WarningRecordMapper.class);
        roleMapper = Mockito.mock(SysRoleMapper.class);
        userRoleMapper = Mockito.mock(SysUserRoleMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        service = new NotifyService(notifyMapper, warningMapper, roleMapper, userRoleMapper, studentMapper);
    }

    private WarningRecord warning(Long id, Integer level, String title) {
        WarningRecord w = new WarningRecord();
        w.setId(id);
        w.setWarningLevel(level);
        w.setTitle(title);
        w.setContent("预警内容");
        return w;
    }

    private void mockRoles(long roleId, String code, long... userIds) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(code);
        Mockito.when(roleMapper.selectList(any())).thenReturn(List.of(role));

        List<SysUserRole> refs = new java.util.ArrayList<>();
        for (long uid : userIds) {
            SysUserRole ref = new SysUserRole();
            ref.setUserId(uid);
            ref.setRoleId(roleId);
            refs.add(ref);
        }
        Mockito.when(userRoleMapper.selectList(any())).thenReturn(refs);
    }

    // ==================== 角色解析 ====================

    @Test
    @DisplayName("按角色解析接收用户")
    void testResolveUserIds() {
        mockRoles(1L, "ADMIN", 1L, 2L);
        assertEquals(Set.of(1L, 2L), service.resolveUserIds(List.of("ADMIN")));
    }

    @Test
    @DisplayName("无匹配角色返回空集合")
    void testResolveEmpty() {
        Mockito.when(roleMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.resolveUserIds(List.of("ADMIN")).isEmpty());
    }

    // ==================== 预警同步 ====================

    @Test
    @DisplayName("预警同步为每个用户生成一条通知")
    void testSyncFromWarnings() {
        mockRoles(1L, "ADMIN", 1L, 2L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(
                warning(10L, 4, "成绩下滑"), warning(11L, 3, "出勤异常")));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(0L);

        int count = service.syncFromWarnings(null);
        assertEquals(4, count);
        Mockito.verify(notifyMapper, Mockito.times(4)).insert(any(NotifyMessage.class));
    }

    @Test
    @DisplayName("已存在通知不重复推送")
    void testSyncDedupe() {
        mockRoles(1L, "ADMIN", 1L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(warning(10L, 4, "成绩下滑")));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(1L);

        assertEquals(0, service.syncFromWarnings(null));
        Mockito.verify(notifyMapper, Mockito.never()).insert(any(NotifyMessage.class));
    }

    @Test
    @DisplayName("无预警时不生成通知")
    void testSyncNoWarnings() {
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, service.syncFromWarnings(null));
    }

    @Test
    @DisplayName("无接收用户时不生成通知")
    void testSyncNoReceivers() {
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(warning(10L, 4, "x")));
        Mockito.when(roleMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, service.syncFromWarnings(null));
    }

    @ParameterizedTest
    @CsvSource({"4, 预警", "3, 预警", "2, 关注", "1, 关注"})
    @DisplayName("通知标题按等级分级并采用关怀化措辞")
    void testTitleLevelPrefix(Integer level, String expectedMark) {
        mockRoles(1L, "ADMIN", 1L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(warning(10L, level, "标题")));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(0L);

        service.syncFromWarnings(null);
        var captor = org.mockito.ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(notifyMapper).insert(captor.capture());
        String title = captor.getValue().getTitle();
        assertTrue(title.contains(expectedMark), "标题应包含分级标记：" + title);
        // 关怀化：不应出现"通报/违规/处罚"等指责性措辞
        assertFalse(title.contains("通报") || title.contains("处罚"), "标题不应含指责性措辞");
    }

    @ParameterizedTest
    @CsvSource({"4, IN_APP+SMS+WECOM", "3, IN_APP+SMS", "2, IN_APP", "1, IN_APP"})
    @DisplayName("通知渠道按预警等级分级")
    void testChannelByLevel(Integer level, String expectedChannel) {
        mockRoles(1L, "ADMIN", 1L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(warning(10L, level, "标题")));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(0L);

        service.syncFromWarnings(null);
        var captor = org.mockito.ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(notifyMapper).insert(captor.capture());
        assertEquals(expectedChannel, captor.getValue().getChannel());
    }

    @Test
    @DisplayName("通知正文包含建议动作，可直接使用")
    void testContentHasActionAdvice() {
        mockRoles(1L, "ADMIN", 1L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(warning(10L, 4, "标题")));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(0L);

        service.syncFromWarnings(null);
        var captor = org.mockito.ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(notifyMapper).insert(captor.capture());
        String content = captor.getValue().getContent();
        assertTrue(content.contains("立即响应"), "红色预警正文应给出立即响应的建议：" + content);
    }

    @Test
    @DisplayName("通知标题使用学生真实姓名")
    void testTitleUsesStudentName() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");
        Mockito.when(studentMapper.selectById(1L)).thenReturn(student);
        mockRoles(1L, "ADMIN", 1L);
        WarningRecord w = warning(10L, 2, "标题");
        w.setStudentId(1L);
        Mockito.when(warningMapper.selectList(any())).thenReturn(List.of(w));
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(0L);

        service.syncFromWarnings(null);
        var captor = org.mockito.ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(notifyMapper).insert(captor.capture());
        assertTrue(captor.getValue().getTitle().contains("张三"));
    }

    // ==================== 已读管理 ====================

    @Test
    @DisplayName("标记已读成功")
    void testMarkRead() {
        NotifyMessage msg = new NotifyMessage();
        msg.setId(1L);
        msg.setUserId(1L);
        msg.setIsRead(0);
        Mockito.when(notifyMapper.selectById(1L)).thenReturn(msg);
        Mockito.when(notifyMapper.updateById(any(NotifyMessage.class))).thenReturn(1);

        assertTrue(service.markRead(1L, 1L));
        assertEquals(1, msg.getIsRead());
        assertNotNull(msg.getReadTime());
    }

    @Test
    @DisplayName("非本人通知不能标记已读")
    void testMarkReadOtherUser() {
        NotifyMessage msg = new NotifyMessage();
        msg.setId(1L);
        msg.setUserId(1L);
        Mockito.when(notifyMapper.selectById(1L)).thenReturn(msg);
        assertFalse(service.markRead(1L, 2L));
    }

    @Test
    @DisplayName("通知不存在返回 false")
    void testMarkReadMissing() {
        Mockito.when(notifyMapper.selectById(99L)).thenReturn(null);
        assertFalse(service.markRead(99L, 1L));
    }

    @Test
    @DisplayName("全部标记已读")
    void testMarkAllRead() {
        NotifyMessage a = new NotifyMessage();
        a.setId(1L);
        a.setIsRead(0);
        NotifyMessage b = new NotifyMessage();
        b.setId(2L);
        b.setIsRead(0);
        Mockito.when(notifyMapper.selectList(any())).thenReturn(List.of(a, b));

        assertEquals(2, service.markAllRead(1L));
        assertEquals(1, a.getIsRead());
        assertEquals(1, b.getIsRead());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 5, 100})
    @DisplayName("未读数量原样返回")
    void testUnreadCount(long count) {
        Mockito.when(notifyMapper.selectCount(any())).thenReturn(count);
        assertEquals(count, service.unreadCount(1L));
    }

    @Test
    @DisplayName("发送系统通知字段完整")
    void testSendToUser() {
        service.sendToUser(3L, "标题", "内容", "SYSTEM", 7L);
        var captor = org.mockito.ArgumentCaptor.forClass(NotifyMessage.class);
        Mockito.verify(notifyMapper).insert(captor.capture());
        NotifyMessage msg = captor.getValue();
        assertEquals(3L, msg.getUserId());
        assertEquals("标题", msg.getTitle());
        assertEquals("SYSTEM", msg.getBizType());
        assertEquals(7L, msg.getBizId());
        assertEquals("IN_APP", msg.getChannel());
        assertEquals(0, msg.getIsDeleted());
        assertNotNull(msg.getCreateTime());
    }

    @Test
    @DisplayName("仅查未读时附加条件")
    void testListUnreadOnly() {
        service.listByUser(1L, true);
        Mockito.verify(notifyMapper).selectList(ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifyMessage>>any());
    }
}
