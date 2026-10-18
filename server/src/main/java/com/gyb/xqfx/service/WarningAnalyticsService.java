package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.College;
import com.gyb.xqfx.entity.InterventionLog;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CollegeMapper;
import com.gyb.xqfx.mapper.InterventionLogMapper;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.model.vo.WarningAnalyticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警分析服务
 *
 * <p>提供三类能力：
 * <ul>
 *   <li>多维下钻：按学院/专业/年级/性别统计预警分布，支撑教学管理规律分析</li>
 *   <li>风险事件时间线：把分散在预警、干预、成绩、考勤中的异常信号按时间串联，
 *       让辅导员能直接回答"什么时候开始恶化、具体发生了什么"</li>
 *   <li>学业风险触发判定：欠学分、成绩趋势下滑、绩点偏离等单维度极端情况
 *       即使综合概率不高也应进入预警</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningAnalyticsService {

    private final WarningRecordMapper warningRecordMapper;
    private final StudentMapper studentMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final InterventionTicketMapper ticketMapper;
    private final InterventionLogMapper logMapper;
    private final AcademicIndicatorService indicatorService;

    /**
     * 多维下钻统计
     *
     * @param termId    学期，为空表示全部
     * @param dimension COLLEGE / MAJOR / GRADE / GENDER / CLAZZ
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public List<WarningAnalyticsVO.DrillItem> drillDown(Long termId, String dimension,
                                                        java.util.List<Long> studentIds) {
        String dim = dimension == null ? "COLLEGE" : dimension.toUpperCase();
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>()
                        .in(studentIds != null && !studentIds.isEmpty(), Student::getId, studentIds));
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(termId != null, WarningRecord::getTermId, termId)
                        .in(studentIds != null && !studentIds.isEmpty(), WarningRecord::getStudentId, studentIds));

        Map<Long, Integer> warningByStudent = new LinkedHashMap<>();
        Map<Long, List<WarningRecord>> warningsOf = new LinkedHashMap<>();
        for (WarningRecord w : warnings) {
            if (w.getStudentId() == null) {
                continue;
            }
            warningByStudent.merge(w.getStudentId(), 1, Integer::sum);
            warningsOf.computeIfAbsent(w.getStudentId(), k -> new ArrayList<>()).add(w);
        }

        Map<String, List<Student>> grouped = new LinkedHashMap<>();
        for (Student s : students) {
            grouped.computeIfAbsent(dimensionValue(s, dim), k -> new ArrayList<>()).add(s);
        }

        List<WarningAnalyticsVO.DrillItem> result = new ArrayList<>();
        grouped.forEach((name, list) -> {
            WarningAnalyticsVO.DrillItem item = new WarningAnalyticsVO.DrillItem();
            item.setName(name);
            item.setStudentCount(list.size());
            int warningCount = list.stream()
                    .mapToInt(s -> warningByStudent.getOrDefault(s.getId(), 0)).sum();
            item.setWarningCount(warningCount);
            item.setWarningRate(rate(warningCount, list.size()));

            int blue = 0, yellow = 0, orange = 0, red = 0, handled = 0;
            for (Student s : list) {
                for (WarningRecord w : warningsOf.getOrDefault(s.getId(), List.of())) {
                    if (w.getWarningLevel() == null) {
                        continue;
                    }
                    switch (w.getWarningLevel()) {
                        case 1 -> blue++;
                        case 2 -> yellow++;
                        case 3 -> orange++;
                        case 4 -> red++;
                        default -> { }
                    }
                    if (w.getStatus() != null && !"ACTIVE".equals(w.getStatus())) {
                        handled++;
                    }
                }
            }
            item.setBlueCount(blue);
            item.setYellowCount(yellow);
            item.setOrangeCount(orange);
            item.setRedCount(red);
            item.setHandledCount(handled);
            item.setHandleRate(rate(handled, warningCount));
            result.add(item);
        });
        result.sort(Comparator.comparing(WarningAnalyticsVO.DrillItem::getWarningCount).reversed());
        return result;
    }

    /**
     * 学生风险事件时间线
     */
    public List<WarningAnalyticsVO.TimelineItem> timeline(Long studentId) {
        List<WarningAnalyticsVO.TimelineItem> items = new ArrayList<>();

        // 预警事件
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId));
        for (WarningRecord w : warnings) {
            WarningAnalyticsVO.TimelineItem item = new WarningAnalyticsVO.TimelineItem();
            item.setEventTime(w.getCreateTime());
            item.setEventType("WARNING");
            item.setEventTypeText("预警");
            item.setTitle(levelText(w.getWarningLevel()) + "预警");
            item.setDetail(w.getTriggerDetail());
            item.setRefId(w.getId());
            item.setSeverity(w.getWarningLevel() != null && w.getWarningLevel() >= 3 ? "SEVERE" : "ATTENTION");
            items.add(item);
        }

        // 干预事件
        List<InterventionTicket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<InterventionTicket>().eq(InterventionTicket::getStudentId, studentId));
        for (InterventionTicket t : tickets) {
            List<InterventionLog> logs = logMapper.selectList(
                    new LambdaQueryWrapper<InterventionLog>().eq(InterventionLog::getTicketId, t.getId()));
            for (InterventionLog log : logs) {
                WarningAnalyticsVO.TimelineItem item = new WarningAnalyticsVO.TimelineItem();
                item.setEventTime(log.getCreateTime());
                item.setEventType("INTERVENTION");
                item.setEventTypeText("干预");
                item.setTitle(actionText(log.getActionType()));
                item.setDetail(log.getActionContent());
                item.setRefId(t.getId());
                item.setSeverity("NORMAL");
                items.add(item);
            }
        }

        // 成绩异动
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .isNotNull(Score::getTotalScore));
        for (Score sc : scores) {
            boolean fail = sc.getIsPass() != null ? sc.getIsPass() == 0
                    : sc.getTotalScore().compareTo(new BigDecimal("60")) < 0;
            if (!fail) {
                continue;
            }
            WarningAnalyticsVO.TimelineItem item = new WarningAnalyticsVO.TimelineItem();
            item.setEventTime(sc.getUpdateTime() == null ? sc.getCreateTime() : sc.getUpdateTime());
            item.setEventType("SCORE");
            item.setEventTypeText("成绩");
            item.setTitle("课程不及格（" + sc.getTotalScore() + " 分）");
            item.setDetail("课程ID " + sc.getCourseId());
            item.setRefId(sc.getId());
            item.setSeverity("ATTENTION");
            items.add(item);
        }

        // 考勤异常
        List<Attendance> attendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId));
        long absent = attendances.stream().filter(a -> "ABSENT".equals(a.getStatus())).count();
        for (Attendance a : attendances) {
            if (!"ABSENT".equals(a.getStatus())) {
                continue;
            }
            WarningAnalyticsVO.TimelineItem item = new WarningAnalyticsVO.TimelineItem();
            item.setEventTime(a.getCheckDate() == null ? a.getCreateTime() : a.getCheckDate().atStartOfDay());
            item.setEventType("ATTENDANCE");
            item.setEventTypeText("考勤");
            item.setTitle("缺勤");
            item.setDetail("累计缺勤 " + absent + " 次");
            item.setRefId(a.getId());
            item.setSeverity(absent >= 5 ? "SEVERE" : "ATTENTION");
            items.add(item);
        }

        items.sort(Comparator.comparing(
                WarningAnalyticsVO.TimelineItem::getEventTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    /**
     * 学业风险触发判定：单维度极端情况即使综合概率不高也应预警
     *
     * <p>规则：欠学分 ≥8 或趋势斜率 ≤-8 判为橙色(3)；
     * 欠学分 ≥4 或趋势斜率 ≤-4 或绩点偏离 ≤-1 判为黄色(2)；否则不触发。</p>
     */
    public WarningAnalyticsVO.AcademicTrigger evalAcademicTrigger(Long studentId, Long termId) {
        AcademicIndicatorVO vo = indicatorService.calculate(studentId, termId);
        WarningAnalyticsVO.AcademicTrigger trigger = new WarningAnalyticsVO.AcademicTrigger();
        trigger.setDeficitCredit(vo.getDeficitCredit());
        trigger.setTrendSlope(vo.getTrendSlope());
        trigger.setDeviation(vo.getDeviation());

        BigDecimal deficit = vo.getDeficitCredit() == null ? BigDecimal.ZERO : vo.getDeficitCredit();
        BigDecimal slope = vo.getTrendSlope() == null ? BigDecimal.ZERO : vo.getTrendSlope();
        BigDecimal dev = vo.getDeviation() == null ? BigDecimal.ZERO : vo.getDeviation();

        if (deficit.compareTo(new BigDecimal("8")) >= 0 || slope.compareTo(new BigDecimal("-8")) <= 0) {
            trigger.setTriggered(true);
            trigger.setSuggestLevel(3);
        } else if (deficit.compareTo(new BigDecimal("4")) >= 0
                || slope.compareTo(new BigDecimal("-4")) <= 0
                || dev.compareTo(new BigDecimal("-1")) <= 0) {
            trigger.setTriggered(true);
            trigger.setSuggestLevel(2);
        } else {
            trigger.setTriggered(false);
            trigger.setSuggestLevel(0);
        }
        trigger.setReason(vo.getReason());
        return trigger;
    }

    private String dimensionValue(Student s, String dim) {
        return switch (dim) {
            case "MAJOR" -> nameOf(majorMapper.selectById(s.getMajorId()), "未分配专业");
            case "GRADE" -> s.getGrade() == null || s.getGrade().isBlank() ? "未知年级" : s.getGrade() + "级";
            case "GENDER" -> genderText(s.getGender());
            case "CLAZZ" -> {
                Clazz c = s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
                yield c == null ? "未分配班级" : c.getClazzName();
            }
            default -> nameOf(collegeMapper.selectById(s.getCollegeId()), "未分配学院");
        };
    }

    private String nameOf(Object entity, String fallback) {
        if (entity instanceof College c) {
            return c.getCollegeName() == null ? fallback : c.getCollegeName();
        }
        if (entity instanceof Major m) {
            return m.getMajorName() == null ? fallback : m.getMajorName();
        }
        return fallback;
    }

    private String genderText(String gender) {
        if (gender == null) {
            return "未知";
        }
        return switch (gender.toUpperCase()) {
            case "M", "MALE", "男" -> "男";
            case "F", "FEMALE", "女" -> "女";
            default -> gender;
        };
    }

    private String levelText(Integer level) {
        return switch (level == null ? 0 : level) {
            case 1 -> "蓝色";
            case 2 -> "黄色";
            case 3 -> "橙色";
            case 4 -> "红色";
            default -> "未分级";
        };
    }

    private String actionText(String actionType) {
        if (actionType == null) {
            return "干预记录";
        }
        return switch (actionType) {
            case "CREATE" -> "创建工单";
            case "ASSIGN" -> "指派责任人";
            case "ACTION" -> "帮扶行动";
            case "EVALUATE" -> "效果评估";
            case "CLOSE" -> "关闭工单";
            default -> actionType;
        };
    }

    private BigDecimal rate(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
