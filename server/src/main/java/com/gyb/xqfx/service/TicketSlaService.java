package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.TicketSlaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工单时效（SLA）管理服务
 *
 * <p>行业实践表明：缺少超时监控的预警系统会退化为"发了预警但没人处理"的
 * 形式主义工具。本服务提供承诺时限设定、超时扫描、逐级升级与催办，
 * 并统计处置及时率以衡量预警项目的真实执行力。</p>
 *
 * <p>时限约定：蓝色 7 天、黄色 3 天、橙色 1 天、红色 8 小时。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketSlaService {

    /** 各预警等级的承诺处理时限（小时） */
    private static final Map<Integer, Integer> SLA_HOURS = Map.of(
            1, 7 * 24,   // 蓝色提示：7 天
            2, 3 * 24,   // 黄色关注：3 天
            3, 24,       // 橙色预警：1 天
            4, 8         // 红色干预：8 小时
    );

    private static final int DEFAULT_SLA_HOURS = 3 * 24;

    /** 升级层级上限：0 未升级 / 1 院系 / 2 校级 */
    private static final int MAX_ESCALATE_LEVEL = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InterventionTicketMapper ticketMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final SysUserMapper userMapper;

    /**
     * 按预警等级取承诺处理时限（小时）
     */
    public int slaHoursOf(Integer level) {
        // Map.of 为不可变映射，不接受 null 键，需先判空
        if (level == null) {
            return DEFAULT_SLA_HOURS;
        }
        return SLA_HOURS.getOrDefault(level, DEFAULT_SLA_HOURS);
    }

    /**
     * 创建工单时设定时效：依据关联预警等级
     */
    public void applySla(InterventionTicket ticket, Integer warningLevel) {
        ticket.setSlaHours(slaHoursOf(warningLevel));
        ticket.setOverdueHours(0);
        ticket.setEscalateLevel(0);
    }

    /**
     * 扫描未结案工单：计算超时并执行升级与催办
     *
     * @return 本次超时的工单数
     */
    public int scanOverdue() {
        List<InterventionTicket> pending = ticketMapper.selectList(
                new LambdaQueryWrapper<InterventionTicket>()
                        .in(InterventionTicket::getStatus, List.of("OPEN", "PROCESSING")));
        int overdue = 0;
        LocalDateTime now = LocalDateTime.now();
        for (InterventionTicket ticket : pending) {
            Integer overdueHours = overdueHours(ticket, now);
            ticket.setOverdueHours(overdueHours);
            if (overdueHours > 0) {
                overdue++;
                int target = targetEscalateLevel(overdueHours, slaOf(ticket));
                if (target > nullValue(ticket.getEscalateLevel())) {
                    ticket.setEscalateLevel(target);
                    ticket.setLastRemindTime(now);
                    log.info("工单 {} 超时 {} 小时，升级至层级 {}", ticket.getTicketNo(), overdueHours, target);
                }
            }
            ticket.setUpdateTime(now);
            ticketMapper.updateById(ticket);
        }
        log.info("SLA 扫描完成：未结案 {} 条，超时 {} 条", pending.size(), overdue);
        return overdue;
    }

    /**
     * 超时时长（小时），未超时返回 0
     */
    public int overdueHours(InterventionTicket ticket, LocalDateTime now) {
        LocalDateTime start = ticket.getStartDate() != null
                ? ticket.getStartDate().atStartOfDay()
                : ticket.getCreateTime();
        if (start == null) {
            return 0;
        }
        LocalDateTime deadline = start.plusHours(slaOf(ticket));
        LocalDateTime end = "CLOSED".equals(ticket.getStatus()) && ticket.getCloseDate() != null
                ? ticket.getCloseDate().atStartOfDay()
                : now;
        if (!end.isAfter(deadline)) {
            return 0;
        }
        return (int) Duration.between(deadline, end).toHours();
    }

    /**
     * 辅导员对预警有效性打标，用于规则调优
     */
    public boolean feedback(Long ticketId, String feedback, String note) {
        InterventionTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在：" + ticketId);
        }
        ticket.setFeedback(feedback);
        ticket.setFeedbackNote(note);
        ticket.setUpdateTime(LocalDateTime.now());
        return ticketMapper.updateById(ticket) > 0;
    }

    /**
     * 处置时效统计：及时率、结案率、平均处理时长、超时分布、责任人排行
     */
    public TicketSlaVO statistics() {
        List<InterventionTicket> all = ticketMapper.selectList(null);
        TicketSlaVO vo = new TicketSlaVO();
        vo.setTotalCount(all.size());
        LocalDateTime now = LocalDateTime.now();

        long closed = all.stream().filter(t -> "CLOSED".equals(t.getStatus())).count();
        long escalated = all.stream()
                .filter(t -> t.getEscalateLevel() != null && t.getEscalateLevel() > 0).count();

        int overdue = 0;
        long totalHours = 0;
        int handledCount = 0;
        for (InterventionTicket t : all) {
            int oh = overdueHours(t, now);
            if (oh > 0) {
                overdue++;
            }
            if ("CLOSED".equals(t.getStatus()) && t.getStartDate() != null && t.getCloseDate() != null) {
                long h = Duration.between(t.getStartDate().atStartOfDay(),
                        t.getCloseDate().atStartOfDay()).toHours();
                totalHours += h;
                handledCount++;
            }
        }
        vo.setClosedCount((int) closed);
        vo.setOverdueCount(overdue);
        vo.setEscalatedCount((int) escalated);
        vo.setOnTimeRate(rate(all.size() - overdue, all.size()));
        vo.setCloseRate(rate((int) closed, all.size()));
        vo.setAvgHandleHours(handledCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalHours).divide(BigDecimal.valueOf(handledCount), 1, RoundingMode.HALF_UP));

        // 超时时长分布
        int[] buckets = new int[4];
        for (InterventionTicket t : all) {
            int oh = overdueHours(t, now);
            int idx = oh <= 0 ? 0 : oh <= 24 ? 1 : oh <= 72 ? 2 : 3;
            buckets[idx]++;
        }
        String[] names = {"未超时", "超时1天内", "超时1-3天", "超时3天以上"};
        List<TicketSlaVO.NameValue> dist = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            dist.add(new TicketSlaVO.NameValue(names[i], buckets[i]));
        }
        vo.setOverdueDistribution(dist);

        // 反馈打标分布
        Map<String, Integer> feedbacks = new LinkedHashMap<>();
        for (InterventionTicket t : all) {
            if (t.getFeedback() != null && !t.getFeedback().isBlank()) {
                feedbacks.merge(t.getFeedback(), 1, Integer::sum);
            }
        }
        vo.setFeedbackDistribution(feedbacks);

        // 责任人处置情况
        Map<Long, List<InterventionTicket>> byOwner = new LinkedHashMap<>();
        for (InterventionTicket t : all) {
            if (t.getOwnerUserId() != null) {
                byOwner.computeIfAbsent(t.getOwnerUserId(), k -> new ArrayList<>()).add(t);
            }
        }
        List<TicketSlaVO.OwnerStat> owners = new ArrayList<>();
        byOwner.forEach((uid, list) -> {
            TicketSlaVO.OwnerStat stat = new TicketSlaVO.OwnerStat();
            stat.setOwnerUserId(uid);
            SysUser user = userMapper.selectById(uid);
            stat.setOwnerName(user == null ? ("用户" + uid) : user.getRealName());
            stat.setTotalCount(list.size());
            int od = (int) list.stream().filter(t -> overdueHours(t, now) > 0).count();
            stat.setOverdueCount(od);
            stat.setClosedCount((int) list.stream().filter(t -> "CLOSED".equals(t.getStatus())).count());
            stat.setOnTimeRate(rate(list.size() - od, list.size()));
            owners.add(stat);
        });
        owners.sort(Comparator.comparing(TicketSlaVO.OwnerStat::getOnTimeRate)
                .thenComparing(Comparator.comparing(TicketSlaVO.OwnerStat::getTotalCount).reversed()));
        vo.setOwnerStats(owners);
        return vo;
    }

    /**
     * 取工单关联预警的等级
     */
    public Integer warningLevelOf(Long warningId) {
        if (warningId == null) {
            return null;
        }
        WarningRecord record = warningRecordMapper.selectById(warningId);
        return record == null ? null : record.getWarningLevel();
    }

    /** 依据超时时长判定应处的升级层级：超 24h 升至院系，超 72h 升至校级 */
    public int targetEscalateLevel(int overdueHours, int slaHours) {
        if (overdueHours <= 0) {
            return 0;
        }
        if (overdueHours >= 72) {
            return MAX_ESCALATE_LEVEL;
        }
        if (overdueHours >= 24) {
            return 1;
        }
        // 红色等短时限工单：超时即提醒
        return slaHours <= 8 ? 1 : 0;
    }

    private int slaOf(InterventionTicket ticket) {
        return ticket.getSlaHours() == null ? DEFAULT_SLA_HOURS : ticket.getSlaHours();
    }

    private int nullValue(Integer v) {
        return v == null ? 0 : v;
    }

    private BigDecimal rate(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
