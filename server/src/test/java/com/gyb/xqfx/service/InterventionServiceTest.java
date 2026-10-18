package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.InterventionLog;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionLogMapper;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 干预闭环服务测试
 */
@DisplayName("干预闭环服务测试")
class InterventionServiceTest {

    private InterventionTicketMapper ticketMapper;
    private InterventionLogMapper logMapper;
    private WarningRecordMapper warningRecordMapper;
    private InterventionService service;
    private TicketSlaService slaService;

    @BeforeEach
    void setUp() {
        ticketMapper = Mockito.mock(InterventionTicketMapper.class);
        logMapper = Mockito.mock(InterventionLogMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        StudentMapper studentMapper = Mockito.mock(StudentMapper.class);
        slaService = Mockito.mock(TicketSlaService.class);
        service = new InterventionService(ticketMapper, logMapper, warningRecordMapper, studentMapper, slaService);
    }

    private WarningRecord activeWarning() {
        WarningRecord w = new WarningRecord();
        w.setId(1L);
        w.setStudentId(101L);
        w.setWarningNo("W001");
        w.setStatus("ACTIVE");
        return w;
    }

    @Test
    @DisplayName("由预警创建工单并自动写入创建日志")
    void testCreateFromWarning() {
        when(warningRecordMapper.selectById(1L)).thenReturn(activeWarning());
        when(ticketMapper.selectCount(any())).thenReturn(0L);
        when(ticketMapper.selectById(any())).thenReturn(new InterventionTicket());

        InterventionTicket ticket = service.createFromWarning(1L, 9L, "TALK", "每周约谈一次", null);

        assertNotNull(ticket.getTicketNo());
        assertTrue(ticket.getTicketNo().startsWith("IT"));
        assertEquals("OPEN", ticket.getStatus());
        assertEquals(101L, ticket.getStudentId());
        assertEquals(9L, ticket.getOwnerUserId());
        assertNotNull(ticket.getDueDate());
        verify(logMapper, times(1)).insert(any(InterventionLog.class));
    }

    @Test
    @DisplayName("预警不存在时建单失败")
    void testCreateWithMissingWarning() {
        when(warningRecordMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.createFromWarning(999L, 9L, "TALK", null, null));
    }

    @Test
    @DisplayName("已关闭预警不可重复建单")
    void testCreateWithClosedWarning() {
        WarningRecord closed = activeWarning();
        closed.setStatus("CLOSED");
        when(warningRecordMapper.selectById(1L)).thenReturn(closed);
        assertThrows(BusinessException.class,
                () -> service.createFromWarning(1L, 9L, "TALK", null, null));
    }

    @Test
    @DisplayName("已存在进行中工单时不可重复建单")
    void testCreateWithExistingTicket() {
        when(warningRecordMapper.selectById(1L)).thenReturn(activeWarning());
        when(ticketMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> service.createFromWarning(1L, 9L, "TALK", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMPROVED", "SLIGHT"})
    @DisplayName("帮扶有效时自动关闭关联预警")
    void testCloseTicketClosesWarning(String effect) {
        InterventionTicket ticket = new InterventionTicket();
        ticket.setId(1L);
        ticket.setStudentId(101L);
        ticket.setStatus("PROCESSING");
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.updateById(ticket)).thenReturn(1);

        WarningRecord handled = new WarningRecord();
        handled.setId(5L);
        handled.setStatus("HANDLED");
        when(warningRecordMapper.selectList(any())).thenReturn(List.of(handled));

        assertTrue(service.closeTicket(1L, "成绩回升", effect));
        assertEquals("CLOSED", ticket.getStatus());
        assertNotNull(ticket.getCloseDate());
        assertEquals("CLOSED", handled.getStatus(), "帮扶有效应关闭预警");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "WORSE"})
    @DisplayName("帮扶无效时保留预警")
    void testCloseTicketKeepsWarning(String effect) {
        InterventionTicket ticket = new InterventionTicket();
        ticket.setId(2L);
        ticket.setStudentId(101L);
        ticket.setStatus("PROCESSING");
        when(ticketMapper.selectById(2L)).thenReturn(ticket);
        when(ticketMapper.updateById(ticket)).thenReturn(1);

        WarningRecord handled = new WarningRecord();
        handled.setStatus("HANDLED");
        when(warningRecordMapper.selectList(any())).thenReturn(List.of(handled));

        assertTrue(service.closeTicket(2L, "无明显变化", effect));
        assertEquals("HANDLED", handled.getStatus(), "帮扶无效不应关闭预警");
    }

    @Test
    @DisplayName("工单已关闭时不可重复关闭")
    void testCloseAlreadyClosed() {
        InterventionTicket ticket = new InterventionTicket();
        ticket.setId(3L);
        ticket.setStatus("CLOSED");
        when(ticketMapper.selectById(3L)).thenReturn(ticket);
        assertThrows(BusinessException.class, () -> service.closeTicket(3L, "x", "IMPROVED"));
    }

    @Test
    @DisplayName("追加干预记录时工单自动流转到处理中")
    void testAppendLogMovesToProcessing() {
        InterventionTicket ticket = new InterventionTicket();
        ticket.setId(4L);
        ticket.setStatus("OPEN");
        when(ticketMapper.selectById(4L)).thenReturn(ticket);

        service.appendLog(4L, "PROCESSING", "已开展第一次辅导", 9L);

        assertEquals("PROCESSING", ticket.getStatus());
        verify(logMapper, times(1)).insert(any(InterventionLog.class));
    }

    @Test
    @DisplayName("工单不存在时追加记录失败")
    void testAppendLogMissingTicket() {
        when(ticketMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.appendLog(999L, "ACTION", "x", 1L));
    }

    @Test
    @DisplayName("默认截止日期为 30 天后")
    void testDefaultDueDate() {
        when(warningRecordMapper.selectById(1L)).thenReturn(activeWarning());
        when(ticketMapper.selectCount(any())).thenReturn(0L);
        when(ticketMapper.selectById(any())).thenReturn(new InterventionTicket());

        InterventionTicket ticket = service.createFromWarning(1L, 9L, "TALK", null, null);
        assertEquals(LocalDate.now().plusDays(30), ticket.getDueDate());
    }
}
