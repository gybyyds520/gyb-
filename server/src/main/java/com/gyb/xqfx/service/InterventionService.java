package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.InterventionLog;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionLogMapper;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.InterventionTicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 干预闭环服务：预警 → 工单 → 帮扶记录 → 效果评估 → 预警降级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionTicketMapper ticketMapper;
    private final InterventionLogMapper logMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final com.gyb.xqfx.mapper.StudentMapper studentMapper;
    private final TicketSlaService ticketSlaService;

    /**
     * 由预警创建干预工单
     */
    @Transactional(rollbackFor = Exception.class)
    public InterventionTicket createFromWarning(Long warningId, Long ownerUserId,
                                                String interveneType, String plan, LocalDate dueDate) {
        WarningRecord warning = warningRecordMapper.selectById(warningId);
        if (warning == null) {
            throw new BusinessException("预警记录不存在");
        }
        if ("CLOSED".equals(warning.getStatus())) {
            throw new BusinessException("该预警已关闭，无需重复建单");
        }

        long openCount = ticketMapper.selectCount(new LambdaQueryWrapper<InterventionTicket>()
                .eq(InterventionTicket::getStudentId, warning.getStudentId())
                .in(InterventionTicket::getStatus, List.of("OPEN", "PROCESSING")));
        if (openCount > 0) {
            throw new BusinessException("该学生已有进行中的干预工单");
        }

        InterventionTicket ticket = new InterventionTicket();
        ticket.setTicketNo("IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ticket.setWarningId(warning.getId());
        ticket.setStudentId(warning.getStudentId());
        ticket.setOwnerUserId(ownerUserId);
        ticket.setInterveneType(interveneType == null ? "TALK" : interveneType);
        ticket.setPlan(plan);
        ticket.setStatus("OPEN");
        ticket.setStartDate(LocalDate.now());
        ticket.setDueDate(dueDate == null ? LocalDate.now().plusDays(30) : dueDate);
        // 按预警等级设定承诺处理时限，超时后由 SLA 服务扫描升级
        ticketSlaService.applySla(ticket, warning.getWarningLevel());
        ticketMapper.insert(ticket);

        // 预警转为处理中
        warning.setStatus("HANDLED");
        warning.setOwnerUserId(ownerUserId);
        warningRecordMapper.updateById(warning);

        appendLog(ticket.getId(), "CREATE", "由预警 " + warning.getWarningNo() + " 创建工单", ownerUserId);
        log.info("干预工单创建：{} 学生 {}", ticket.getTicketNo(), ticket.getStudentId());
        return ticket;
    }

    /**
     * 追加干预过程记录
     */
    @Transactional(rollbackFor = Exception.class)
    public InterventionLog appendLog(Long ticketId, String actionType, String content, Long operatorId) {
        InterventionTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        InterventionLog logEntity = new InterventionLog();
        logEntity.setTicketId(ticketId);
        logEntity.setActionType(actionType);
        logEntity.setActionContent(content);
        logEntity.setActionDate(LocalDate.now());
        logEntity.setOperatorId(operatorId);
        logMapper.insert(logEntity);

        if ("PROCESSING".equals(actionType) && "OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("PROCESSING");
            ticketMapper.updateById(ticket);
        }
        return logEntity;
    }

    /**
     * 关闭工单并做效果评估，同时关闭关联预警
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTicket(Long ticketId, String conclusion, String effectEval) {
        InterventionTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException("工单不存在");
        }
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new BusinessException("工单已关闭");
        }
        ticket.setStatus("CLOSED");
        ticket.setCloseDate(LocalDate.now());
        ticket.setConclusion(conclusion);
        ticket.setEffectEval(effectEval);
        boolean updated = ticketMapper.updateById(ticket) > 0;

        // 帮扶有效时关闭该学生的在处预警
        if ("IMPROVED".equals(effectEval) || "SLIGHT".equals(effectEval)) {
            List<WarningRecord> warnings = warningRecordMapper.selectList(
                    new LambdaQueryWrapper<WarningRecord>()
                            .eq(WarningRecord::getStudentId, ticket.getStudentId())
                            .eq(WarningRecord::getStatus, "HANDLED"));
            for (WarningRecord w : warnings) {
                w.setStatus("CLOSED");
                w.setHandleResult("帮扶有效，预警关闭");
                w.setHandleTime(java.time.LocalDateTime.now());
                warningRecordMapper.updateById(w);
            }
        }
        appendLog(ticketId, "CLOSE", "关闭工单，效果评估：" + effectEval, ticket.getOwnerUserId());
        return updated;
    }

    /**
     * 查询工单列表（含学生信息）
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public List<InterventionTicketVO> listTickets(String status, Long studentId, java.util.List<Long> scopeStudentIds) {
        LambdaQueryWrapper<InterventionTicket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isEmpty(), InterventionTicket::getStatus, status);
        wrapper.eq(studentId != null, InterventionTicket::getStudentId, studentId);
        wrapper.in(scopeStudentIds != null && !scopeStudentIds.isEmpty(),
                InterventionTicket::getStudentId, scopeStudentIds);
        wrapper.orderByDesc(InterventionTicket::getId);

        List<InterventionTicket> tickets = ticketMapper.selectList(wrapper);
        List<Long> studentIds = tickets.stream().map(InterventionTicket::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, com.gyb.xqfx.entity.Student> students = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(com.gyb.xqfx.entity.Student::getId, s -> s, (a, b) -> a));

        return tickets.stream().map(t -> {
            InterventionTicketVO vo = new InterventionTicketVO();
            org.springframework.beans.BeanUtils.copyProperties(t, vo);
            com.gyb.xqfx.entity.Student s = students.get(t.getStudentId());
            if (s != null) {
                vo.setStudentNo(s.getStudentNo());
                vo.setStudentName(s.getName());
            }
            vo.setLogCount(logMapper.selectCount(
                    new LambdaQueryWrapper<InterventionLog>().eq(InterventionLog::getTicketId, t.getId())));
            return vo;
        }).toList();
    }

    /**
     * 查询工单的干预过程记录
     */
    public List<InterventionLog> listLogs(Long ticketId) {
        return logMapper.selectList(new LambdaQueryWrapper<InterventionLog>()
                .eq(InterventionLog::getTicketId, ticketId)
                .orderByAsc(InterventionLog::getId));
    }
}
