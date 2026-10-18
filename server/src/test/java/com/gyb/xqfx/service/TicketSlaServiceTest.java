package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.TicketSlaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 工单时效（SLA）服务测试
 */
@DisplayName("工单时效服务测试")
class TicketSlaServiceTest {

    private InterventionTicketMapper ticketMapper;
    private WarningRecordMapper warningRecordMapper;
    private SysUserMapper userMapper;
    private TicketSlaService service;

    @BeforeEach
    void setUp() {
        ticketMapper = Mockito.mock(InterventionTicketMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        userMapper = Mockito.mock(SysUserMapper.class);
        service = new TicketSlaService(ticketMapper, warningRecordMapper, userMapper);
    }

    private InterventionTicket ticket(String status, int slaHours, LocalDate start, LocalDate close) {
        InterventionTicket t = new InterventionTicket();
        t.setId(1L);
        t.setTicketNo("IT001");
        t.setOwnerUserId(1L);
        t.setStatus(status);
        t.setSlaHours(slaHours);
        t.setStartDate(start);
        t.setCloseDate(close);
        t.setCreateTime(start == null ? LocalDateTime.now() : start.atStartOfDay());
        t.setEscalateLevel(0);
        return t;
    }

    // ==================== 时限设定 ====================

    @ParameterizedTest
    @CsvSource({"1, 168", "2, 72", "3, 24", "4, 8"})
    @DisplayName("各预警等级的承诺时限符合约定")
    void testSlaHoursOf(String level, String expected) {
        assertEquals(Integer.parseInt(expected), service.slaHoursOf(Integer.parseInt(level)));
    }

    @Test
    @DisplayName("未知等级使用默认时限")
    void testSlaHoursDefault() {
        assertEquals(72, service.slaHoursOf(null));
        assertEquals(72, service.slaHoursOf(9));
    }

    @Test
    @DisplayName("建单时按等级写入时限与初始状态")
    void testApplySla() {
        InterventionTicket t = new InterventionTicket();
        service.applySla(t, 4);
        assertEquals(8, t.getSlaHours());
        assertEquals(0, t.getOverdueHours());
        assertEquals(0, t.getEscalateLevel());
    }

    // ==================== 超时计算 ====================

    @Test
    @DisplayName("未超过时限时超时为 0")
    void testNotOverdue() {
        LocalDate today = LocalDate.now();
        InterventionTicket t = ticket("OPEN", 72, today.minusDays(1), null);
        assertEquals(0, service.overdueHours(t, LocalDateTime.now()));
    }

    @Test
    @DisplayName("超过时限时返回超时小时数")
    void testOverdue() {
        LocalDate start = LocalDate.now().minusDays(10);
        InterventionTicket t = ticket("OPEN", 72, start, null);
        int hours = service.overdueHours(t, LocalDateTime.now());
        // 10 天 = 240 小时，减去 72 小时时限 = 168；
        // startDate 按当日 00:00 起算，会额外计入当天已过小时（0~24），窗口取 168~192
        assertTrue(hours >= 168 && hours <= 192, "超时小时数应在 168~192，实际：" + hours);
    }

    @Test
    @DisplayName("已关闭工单按关闭时间计算超时")
    void testClosedOverdue() {
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate close = LocalDate.now().minusDays(5);
        InterventionTicket t = ticket("CLOSED", 72, start, close);
        int hours = service.overdueHours(t, LocalDateTime.now());
        assertTrue(hours > 40 && hours < 60, "关闭时超时约 48 小时，实际：" + hours);
    }

    @Test
    @DisplayName("缺少开始时间时不判超时")
    void testNoStartTime() {
        InterventionTicket t = new InterventionTicket();
        t.setSlaHours(8);
        assertEquals(0, service.overdueHours(t, LocalDateTime.now()));
    }

    // ==================== 升级判定 ====================

    @ParameterizedTest
    @CsvSource({"0, 0", "10, 0", "24, 1", "48, 1", "72, 2", "200, 2"})
    @DisplayName("超时时长决定升级层级")
    void testTargetEscalateLevel(String overdue, String expected) {
        assertEquals(Integer.parseInt(expected),
                service.targetEscalateLevel(Integer.parseInt(overdue), 72));
    }

    @Test
    @DisplayName("短时限工单（红色）超时即提醒")
    void testShortSlaEscalate() {
        assertEquals(1, service.targetEscalateLevel(2, 8));
    }

    @Test
    @DisplayName("扫描超时工单并升级")
    void testScanOverdue() {
        InterventionTicket t = ticket("OPEN", 72, LocalDate.now().minusDays(10), null);
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(t));

        int count = service.scanOverdue();
        assertEquals(1, count);
        assertTrue(t.getOverdueHours() > 0);
        assertTrue(t.getEscalateLevel() > 0, "超时工单应被升级");
        Mockito.verify(ticketMapper).updateById(t);
    }

    @Test
    @DisplayName("未超时工单不升级")
    void testScanNotOverdue() {
        InterventionTicket t = ticket("OPEN", 72, LocalDate.now(), null);
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(t));

        assertEquals(0, service.scanOverdue());
        assertEquals(0, t.getEscalateLevel());
    }

    // ==================== 反馈打标 ====================

    @Test
    @DisplayName("记录辅导员有效性反馈")
    void testFeedback() {
        InterventionTicket t = ticket("CLOSED", 72, LocalDate.now(), LocalDate.now());
        Mockito.when(ticketMapper.selectById(1L)).thenReturn(t);
        Mockito.when(ticketMapper.updateById(any(InterventionTicket.class))).thenReturn(1);
        assertTrue(service.feedback(1L, "FALSE_POSITIVE", "学生在外实习"));
        assertEquals("FALSE_POSITIVE", t.getFeedback());
        assertEquals("学生在外实习", t.getFeedbackNote());
    }

    @Test
    @DisplayName("工单不存在时反馈失败")
    void testFeedbackMissing() {
        Mockito.when(ticketMapper.selectById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.feedback(99L, "EFFECTIVE", null));
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("统计及时率、结案率与责任人排行")
    void testStatistics() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRealName("张老师");
        Mockito.when(userMapper.selectById(1L)).thenReturn(user);
        LocalDate now = LocalDate.now();
        // 一条超时未处理 + 一条按时关闭
        InterventionTicket overdue = ticket("OPEN", 72, now.minusDays(10), null);
        InterventionTicket onTime = ticket("CLOSED", 72, now.minusDays(3), now.minusDays(2));
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(overdue, onTime));

        TicketSlaVO vo = service.statistics();
        assertEquals(2, vo.getTotalCount());
        assertEquals(1, vo.getClosedCount());
        assertEquals(1, vo.getOverdueCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(vo.getOnTimeRate()));
        assertEquals(0, new BigDecimal("50.00").compareTo(vo.getCloseRate()));
        assertEquals(1, vo.getOwnerStats().size());
        assertEquals("张老师", vo.getOwnerStats().get(0).getOwnerName());
        assertEquals(4, vo.getOverdueDistribution().size());
    }

    @Test
    @DisplayName("无工单时统计不报错")
    void testStatisticsEmpty() {
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of());
        TicketSlaVO vo = service.statistics();
        assertEquals(0, vo.getTotalCount());
        assertEquals(0, new BigDecimal("0.00").compareTo(vo.getOnTimeRate()));
    }

    @Test
    @DisplayName("统计反馈打标分布")
    void testFeedbackDistribution() {
        LocalDate now = LocalDate.now();
        InterventionTicket a = ticket("CLOSED", 72, now.minusDays(3), now.minusDays(2));
        a.setFeedback("EFFECTIVE");
        InterventionTicket b = ticket("CLOSED", 72, now.minusDays(3), now.minusDays(2));
        b.setFeedback("FALSE_POSITIVE");
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(a, b));

        assertEquals(1, service.statistics().getFeedbackDistribution().get("EFFECTIVE"));
        assertEquals(1, service.statistics().getFeedbackDistribution().get("FALSE_POSITIVE"));
    }

    @Test
    @DisplayName("查询关联预警等级")
    void testWarningLevelOf() {
        WarningRecord w = new WarningRecord();
        w.setId(5L);
        w.setWarningLevel(3);
        Mockito.when(warningRecordMapper.selectById(5L)).thenReturn(w);
        assertEquals(3, service.warningLevelOf(5L));
        assertNull(service.warningLevelOf(null));
    }
}
