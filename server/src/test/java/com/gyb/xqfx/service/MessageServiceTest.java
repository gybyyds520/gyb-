package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.ImMessage;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.ImMessageMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 站内通讯测试：通讯录按身份关系推导、越权发送拦截、会话与已读
 */
@DisplayName("站内通讯服务测试")
class MessageServiceTest {

    private ImMessageMapper messageMapper;
    private SysUserMapper userMapper;
    private StudentMapper studentMapper;
    private TeacherMapper teacherMapper;
    private StudentParentMapper studentParentMapper;
    private CourseOfferingMapper offeringMapper;
    private ScoreMapper scoreMapper;
    private ClazzMapper clazzMapper;
    private RoleService roleService;
    private DataScopeService dataScopeService;
    private NotifyService notifyService;
    private MessageService service;

    // 账号：教师 101 / 学生 201 / 家长 301
    private static final Long TEACHER_USER = 101L;
    private static final Long STUDENT_USER = 201L;
    private static final Long PARENT_USER = 301L;
    private static final Long STUDENT_ID = 1L;
    private static final Long TEACHER_ID = 11L;
    private static final Long CLAZZ_ID = 21L;

    @BeforeEach
    void setUp() {
        messageMapper = Mockito.mock(ImMessageMapper.class);
        userMapper = Mockito.mock(SysUserMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        teacherMapper = Mockito.mock(TeacherMapper.class);
        studentParentMapper = Mockito.mock(StudentParentMapper.class);
        offeringMapper = Mockito.mock(CourseOfferingMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        roleService = Mockito.mock(RoleService.class);
        dataScopeService = Mockito.mock(DataScopeService.class);
        notifyService = Mockito.mock(NotifyService.class);
        service = new MessageService(messageMapper, userMapper, studentMapper, teacherMapper,
                studentParentMapper, offeringMapper, scoreMapper, clazzMapper,
                roleService, dataScopeService, notifyService);
    }

    private Student student() {
        Student s = new Student();
        s.setId(STUDENT_ID);
        s.setName("张明");
        s.setStudentNo("2300000001");
        s.setClazzId(CLAZZ_ID);
        s.setUserId(STUDENT_USER);
        return s;
    }

    private Teacher teacher() {
        Teacher t = new Teacher();
        t.setId(TEACHER_ID);
        t.setName("李老师");
        t.setUserId(TEACHER_USER);
        return t;
    }

    private Clazz clazz(Long head, Long counselor) {
        Clazz c = new Clazz();
        c.setId(CLAZZ_ID);
        c.setHeadTeacherId(head);
        c.setCounselorId(counselor);
        return c;
    }

    private SysUser user(Long id, String name, String type) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setRealName(name);
        u.setUserType(type);
        return u;
    }

    private void mockNames() {
        Mockito.when(userMapper.selectById(TEACHER_USER)).thenReturn(user(TEACHER_USER, "李老师", "TEACHER"));
        Mockito.when(userMapper.selectById(STUDENT_USER)).thenReturn(user(STUDENT_USER, "张明", "STUDENT"));
        Mockito.when(userMapper.selectById(PARENT_USER)).thenReturn(user(PARENT_USER, "张父", "PARENT"));
    }

    // ==================== 通讯录推导 ====================

    @Test
    @DisplayName("教师通讯录包含其学生及学生家长")
    void testTeacherContacts() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());

        StudentParent sp = new StudentParent();
        sp.setStudentId(STUDENT_ID);
        sp.setParentUserId(PARENT_USER);
        sp.setParentName("张父");
        sp.setRelation("FATHER");
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sp));

        List<MessageService.Contact> contacts = service.contactsOf(TEACHER_USER);
        assertEquals(2, contacts.size());
        assertTrue(contacts.stream().anyMatch(c -> c.getUserId().equals(STUDENT_USER)
                && "STUDENT".equals(c.getRole())));
        assertTrue(contacts.stream().anyMatch(c -> c.getUserId().equals(PARENT_USER)
                && "张明的父亲".equals(c.getRelation())), "家长应标注与学生关系");

    }

    @Test
    @DisplayName("学生通讯录包含家长与班主任")
    void testStudentContacts() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(STUDENT_USER)).thenReturn(Set.of("STUDENT"));
        Mockito.when(roleService.studentIdOf(STUDENT_USER)).thenReturn(STUDENT_ID);
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(clazz(TEACHER_ID, null));
        Mockito.when(teacherMapper.selectById(TEACHER_ID)).thenReturn(teacher());
        Mockito.when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        StudentParent sp = new StudentParent();
        sp.setStudentId(STUDENT_ID);
        sp.setParentUserId(PARENT_USER);
        sp.setParentName("张父");
        sp.setRelation("MOTHER");
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sp));

        List<MessageService.Contact> contacts = service.contactsOf(STUDENT_USER);
        assertEquals(2, contacts.size(), "家长 + 班主任");
        assertTrue(contacts.stream().anyMatch(c -> c.getUserId().equals(PARENT_USER)
                && "我的母亲".equals(c.getRelation())));
        assertTrue(contacts.stream().anyMatch(c -> c.getUserId().equals(TEACHER_USER)
                && "张明的班主任".equals(c.getRelation())));
    }

    @Test
    @DisplayName("学生通讯录包含选修课程的任课教师")
    void testStudentCourseTeacher() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(STUDENT_USER)).thenReturn(Set.of("STUDENT"));
        Mockito.when(roleService.studentIdOf(STUDENT_USER)).thenReturn(STUDENT_ID);
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(clazz(null, null));

        Score sc = new Score();
        sc.setStudentId(STUDENT_ID);
        sc.setCourseOfferingId(31L);
        Mockito.when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sc));
        CourseOffering o = new CourseOffering();
        o.setId(31L);
        o.setTeacherId(TEACHER_ID);
        Mockito.when(offeringMapper.selectById(31L)).thenReturn(o);
        Mockito.when(teacherMapper.selectById(TEACHER_ID)).thenReturn(teacher());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<MessageService.Contact> contacts = service.contactsOf(STUDENT_USER);
        assertEquals(1, contacts.size());
        assertEquals("张明的任课教师", contacts.get(0).getRelation());
    }

    @Test
    @DisplayName("家长通讯录包含子女及子女的辅导员")
    void testParentContacts() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(PARENT_USER)).thenReturn(Set.of("PARENT"));
        Mockito.when(dataScopeService.childStudentIds(PARENT_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(clazz(null, TEACHER_ID));
        Mockito.when(teacherMapper.selectById(TEACHER_ID)).thenReturn(teacher());
        Mockito.when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<MessageService.Contact> contacts = service.contactsOf(PARENT_USER);
        assertEquals(2, contacts.size(), "子女 + 辅导员");
        assertTrue(contacts.stream().anyMatch(c -> c.getUserId().equals(STUDENT_USER)
                && "我的孩子".equals(c.getRelation())));
        assertTrue(contacts.stream().anyMatch(c -> "张明的辅导员".equals(c.getRelation())));
    }

    @Test
    @DisplayName("通讯录不含本人")
    void testExcludeSelf() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(STUDENT_USER)).thenReturn(Set.of("STUDENT"));
        Mockito.when(roleService.studentIdOf(STUDENT_USER)).thenReturn(STUDENT_ID);
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(clazz(null, null));
        Mockito.when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertTrue(service.contactsOf(STUDENT_USER).isEmpty(), "无关系人时通讯录为空");
    }

    // ==================== 发送与越权 ====================

    @Test
    @DisplayName("教师向学生发送消息成功并推送通知")
    void testSend() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        Mockito.doAnswer(inv -> {
            ImMessage m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(messageMapper).insert(any(ImMessage.class));

        MessageService.MessageVO vo = service.sendAs(TEACHER_USER, STUDENT_USER, "下周面谈学业情况");
        assertEquals("下周面谈学业情况", vo.getContent());
        assertEquals(TEACHER_USER, vo.getSenderId());
        assertEquals(STUDENT_USER, vo.getReceiverId());
        assertEquals(0, vo.getIsRead());
        assertTrue(vo.getMine());
        Mockito.verify(notifyService).sendToUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("向通讯录外的人发消息被拒绝")
    void testSendForbidden() {
        mockNames();
        // 教师 101 的可见范围不含该学生 -> 通讯录里没有学生 201
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.sendAs(TEACHER_USER, STUDENT_USER, "越权消息"));
        Mockito.verify(messageMapper, Mockito.never()).insert(any(ImMessage.class));
    }

    @Test
    @DisplayName("空内容、超长内容、发给自己均被拒绝")
    void testSendValidation() {
        assertThrows(BusinessException.class, () -> service.sendAs(TEACHER_USER, STUDENT_USER, "  "));
        assertThrows(BusinessException.class, () -> service.sendAs(TEACHER_USER, STUDENT_USER, null));
        assertThrows(BusinessException.class,
                () -> service.sendAs(TEACHER_USER, STUDENT_USER, "x".repeat(1001)));
        assertThrows(BusinessException.class,
                () -> service.sendAs(TEACHER_USER, TEACHER_USER, "给自己"));
        assertThrows(BusinessException.class, () -> service.sendAs(TEACHER_USER, null, "无接收人"));
    }

    // ==================== 会话与已读 ====================

    @Test
    @DisplayName("会话列表汇总未读数与最后一条消息")
    void testConversations() {
        mockNames();
        LocalDateTime now = LocalDateTime.now();
        Mockito.when(messageMapper.conversations(TEACHER_USER)).thenReturn(List.of(
                Map.of("peerId", STUDENT_USER, "lastTime", now, "unreadCount", 2)));
        ImMessage last = new ImMessage();
        last.setContent("老师，我最近在按错题本复习");
        last.setCreateTime(now);
        Mockito.when(messageMapper.lastMessage(TEACHER_USER, STUDENT_USER)).thenReturn(last);

        List<MessageService.Conversation> list = service.conversationsOf(TEACHER_USER);
        assertEquals(1, list.size());
        assertEquals(STUDENT_USER, list.get(0).getPeerId());
        assertEquals("张明", list.get(0).getPeerName());
        assertEquals("STUDENT", list.get(0).getPeerRole());
        assertEquals(2, list.get(0).getUnreadCount());
        assertEquals("老师，我最近在按错题本复习", list.get(0).getLastContent());
        assertEquals(now, list.get(0).getLastTime());
    }

    @Test
    @DisplayName("会话摘要过长时截断")
    void testConversationAbbreviate() {
        mockNames();
        Mockito.when(messageMapper.conversations(TEACHER_USER)).thenReturn(List.of(
                Map.of("peerId", STUDENT_USER, "lastTime", LocalDateTime.now(), "unreadCount", 0)));
        ImMessage last = new ImMessage();
        last.setContent("长".repeat(80));
        Mockito.when(messageMapper.lastMessage(TEACHER_USER, STUDENT_USER)).thenReturn(last);

        String summary = service.conversationsOf(TEACHER_USER).get(0).getLastContent();
        assertTrue(summary.endsWith("..."));
        assertEquals(63, summary.length(), "60 字正文 + 省略号");
    }

    @Test
    @DisplayName("聊天记录按时间升序并标注收发方向")
    void testHistory() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ImMessage m1 = new ImMessage();
        m1.setId(1L);
        m1.setSenderId(STUDENT_USER);
        m1.setReceiverId(TEACHER_USER);
        m1.setContent("老师好");
        m1.setCreateTime(LocalDateTime.now().minusMinutes(5));
        ImMessage m2 = new ImMessage();
        m2.setId(2L);
        m2.setSenderId(TEACHER_USER);
        m2.setReceiverId(STUDENT_USER);
        m2.setContent("你好，请讲");
        m2.setCreateTime(LocalDateTime.now());
        Mockito.when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1, m2));

        List<MessageService.MessageVO> list = service.historyOf(TEACHER_USER, STUDENT_USER, null);
        assertEquals(2, list.size());
        assertEquals("老师好", list.get(0).getContent());
        assertFalse(list.get(0).getMine(), "对方发来的消息");
        assertEquals("张明", list.get(0).getSenderName());
        assertTrue(list.get(1).getMine(), "我发出的消息");
        assertEquals("李老师", list.get(1).getSenderName());
    }

    @Test
    @DisplayName("查看非通讯录成员的聊天记录被拒绝")
    void testHistoryForbidden() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.historyOf(TEACHER_USER, STUDENT_USER, null));
    }

    @Test
    @DisplayName("标记已读只处理对方发给我的未读消息")
    void testMarkRead() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ImMessage m1 = new ImMessage();
        m1.setId(1L);
        m1.setSenderId(STUDENT_USER);
        m1.setReceiverId(TEACHER_USER);
        m1.setIsRead(0);
        ImMessage m2 = new ImMessage();
        m2.setId(2L);
        m2.setSenderId(STUDENT_USER);
        m2.setReceiverId(TEACHER_USER);
        m2.setIsRead(0);
        Mockito.when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1, m2));

        int n = service.markReadOf(TEACHER_USER, STUDENT_USER);
        assertEquals(2, n);
        assertEquals(1, m1.getIsRead());
        assertEquals(1, m2.getIsRead());
        assertNotNull(m1.getReadTime());
        Mockito.verify(messageMapper, Mockito.times(2)).updateById(any(ImMessage.class));
    }

    @Test
    @DisplayName("标记非通讯录成员的会话已读被拒绝")
    void testMarkReadForbidden() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.markReadOf(TEACHER_USER, STUDENT_USER));
    }

    @Test
    @DisplayName("未读总数与可通讯判定")
    void testUnreadAndCanTalk() {
        mockNames();
        Mockito.when(roleService.roleCodesOf(TEACHER_USER)).thenReturn(Set.of("TEACHER"));
        Mockito.when(dataScopeService.visibleStudentIds(TEACHER_USER)).thenReturn(List.of(STUDENT_ID));
        Mockito.when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        Mockito.when(studentParentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        Mockito.when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertEquals(3L, service.unreadTotalOf(TEACHER_USER));
        assertTrue(service.canTalkTo(TEACHER_USER, STUDENT_USER));
        assertFalse(service.canTalkTo(TEACHER_USER, PARENT_USER), "无家长绑定时不可通讯");
    }
}
