package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import com.gyb.xqfx.model.vo.BigScreenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 校级大屏聚合服务：面向校领导/答辩演示的全校学情驾驶舱。
 * <p>
 * 数据规模为演示级，采用"预警/工单加载后在内存关联聚合"的方式，
 * 避免为驾驶舱编写复杂 SQL；数据量增大时可下沉为 GROUP BY 查询。
 */
@Service
@RequiredArgsConstructor
public class BigScreenService {

    private static final int TREND_WEEKS = 8;
    private static final int LATEST_WARNING_SIZE = 8;
    private static final int LATEST_TICKET_SIZE = 5;

    private static final Map<Integer, String> LEVEL_TEXT = Map.of(1, "蓝色", 2, "黄色", 3, "橙色", 4, "红色");
    private static final Map<String, String> TYPE_TEXT = Map.of(
            "ACADEMIC", "学业", "ATTENDANCE", "出勤", "HOMEWORK", "作业", "FAIL", "挂科",
            "PROGRESS", "进度", "PSYCH", "心理", "BEHAVIOR", "行为", "CONSUMPTION", "消费",
            "COMPREHENSIVE", "综合");

    private final StudentMapper studentMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final InterventionTicketMapper interventionTicketMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final CollegeMapper collegeMapper;

    /** 生成大屏全量数据 */
    public BigScreenVO overview() {
        BigScreenVO vo = new BigScreenVO();

        // 1. 顶部指标带
        BigScreenVO.Overview overview = new BigScreenVO.Overview();
        overview.setStudentCount(studentMapper.selectCount(null));
        overview.setActiveWarning(warningRecordMapper.selectCount(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStatus, "ACTIVE")));
        overview.setSevereWarning(warningRecordMapper.selectCount(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getStatus, "ACTIVE")
                        .ge(WarningRecord::getWarningLevel, 3)));
        overview.setOpenTicket(interventionTicketMapper.selectCount(
                new LambdaQueryWrapper<InterventionTicket>()
                        .in(InterventionTicket::getStatus, "OPEN", "PROCESSING")));
        vo.setOverview(overview);

        // 2. 预警记录（演示规模内加载，内存聚合）
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .orderByDesc(WarningRecord::getCreateTime)
                        .last("limit 5000"));
        Map<Long, Student> students = toMap(studentMapper.selectList(null), Student::getId);

        vo.setWarningTrend(trend(warnings));
        vo.setWarningLevelDist(levelDist(warnings));
        vo.setWarningTypeDist(typeDist(warnings));

        // 3. 组织链路：student -> clazz -> major -> college
        Map<Long, Clazz> clazzes = toMap(clazzMapper.selectList(null), Clazz::getId);
        Map<Long, Major> majors = toMap(majorMapper.selectList(null), Major::getId);
        Map<Long, College> colleges = toMap(collegeMapper.selectList(null), College::getId);

        List<WarningRecord> activeWarnings = warnings.stream()
                .filter(w -> "ACTIVE".equals(w.getStatus()))
                .toList();
        vo.setCollegeRanking(collegeRanking(activeWarnings, students, clazzes, majors, colleges));
        vo.setRiskHeatmap(riskHeatmap(activeWarnings, students, clazzes, majors, colleges));

        // 4. 最新动态
        vo.setLatestWarnings(latestWarnings(warnings, students));
        vo.setLatestTickets(latestTickets(students));
        return vo;
    }

    // ==================== 内部聚合 ====================

    private List<BigScreenVO.TrendItem> trend(List<WarningRecord> warnings) {
        LocalDate today = LocalDate.now();
        // 近 8 周的周一
        List<LocalDate> weekStarts = new ArrayList<>();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        for (int i = TREND_WEEKS - 1; i >= 0; i--) {
            weekStarts.add(monday.minusWeeks(i));
        }
        Map<String, Long> countByWeek = new LinkedHashMap<>();
        weekStarts.forEach(w -> countByWeek.put(key(w), 0L));
        for (WarningRecord w : warnings) {
            LocalDateTime t = w.getCreateTime();
            if (t == null) {
                continue;
            }
            LocalDate day = t.toLocalDate();
            for (LocalDate ws : weekStarts) {
                LocalDate we = ws.plusDays(6);
                if ((day.isEqual(ws) || day.isAfter(ws)) && (day.isEqual(we) || day.isBefore(we))) {
                    countByWeek.computeIfPresent(key(ws), (k, v) -> v + 1);
                    break;
                }
            }
        }
        return countByWeek.entrySet().stream()
                .map(e -> {
                    BigScreenVO.TrendItem item = new BigScreenVO.TrendItem();
                    item.setWeek(e.getKey());
                    item.setCount(e.getValue());
                    return item;
                })
                .toList();
    }

    private String key(LocalDate monday) {
        return monday.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

    private List<BigScreenVO.NameValue> levelDist(List<WarningRecord> warnings) {
        Map<Integer, Long> counts = warnings.stream()
                .filter(w -> w.getWarningLevel() != null)
                .collect(Collectors.groupingBy(WarningRecord::getWarningLevel, Collectors.counting()));
        return LEVEL_TEXT.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(e -> new BigScreenVO.NameValue(e.getValue(),
                        counts.getOrDefault(e.getKey(), 0L)))
                .toList();
    }

    private List<BigScreenVO.NameValue> typeDist(List<WarningRecord> warnings) {
        Map<String, Long> counts = warnings.stream()
                .filter(w -> w.getWarningType() != null)
                .collect(Collectors.groupingBy(WarningRecord::getWarningType, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> new BigScreenVO.NameValue(TYPE_TEXT.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .toList();
    }

    private List<BigScreenVO.CollegeStat> collegeRanking(List<WarningRecord> warnings,
                                                         Map<Long, Student> students,
                                                         Map<Long, Clazz> clazzes,
                                                         Map<Long, Major> majors,
                                                         Map<Long, College> colleges) {
        Map<Long, long[]> byCollege = new HashMap<>(); // collegeId -> [total, severe]
        for (WarningRecord w : warnings) {
            Long collegeId = collegeIdOf(w.getStudentId(), students, clazzes, majors);
            if (collegeId == null) {
                continue;
            }
            long[] arr = byCollege.computeIfAbsent(collegeId, k -> new long[2]);
            arr[0]++;
            if (w.getWarningLevel() != null && w.getWarningLevel() >= 3) {
                arr[1]++;
            }
        }
        return byCollege.entrySet().stream()
                .sorted(Map.Entry.<Long, long[]>comparingByValue(
                        Comparator.comparingLong(a -> -a[0])))
                .map(e -> {
                    BigScreenVO.CollegeStat stat = new BigScreenVO.CollegeStat();
                    College c = colleges.get(e.getKey());
                    stat.setCollegeName(c == null ? ("学院#" + e.getKey()) : c.getCollegeName());
                    stat.setWarningCount(e.getValue()[0]);
                    stat.setSevereCount(e.getValue()[1]);
                    return stat;
                })
                .toList();
    }

    private List<BigScreenVO.HeatCell> riskHeatmap(List<WarningRecord> warnings,
                                                   Map<Long, Student> students,
                                                   Map<Long, Clazz> clazzes,
                                                   Map<Long, Major> majors,
                                                   Map<Long, College> colleges) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WarningRecord w : warnings) {
            Long collegeId = collegeIdOf(w.getStudentId(), students, clazzes, majors);
            if (collegeId == null) {
                continue;
            }
            College c = colleges.get(collegeId);
            Student s = students.get(w.getStudentId());
            Clazz cz = s == null ? null : clazzes.get(s.getClazzId());
            String college = c == null ? ("学院#" + collegeId) : c.getCollegeName();
            String grade = cz == null ? "未知" : (cz.getGrade() == null ? "未知" : cz.getGrade());
            counts.merge(college + "|" + grade, 1L, Long::sum);
        }
        return counts.entrySet().stream().map(e -> {
            BigScreenVO.HeatCell cell = new BigScreenVO.HeatCell();
            String[] parts = e.getKey().split("\\|");
            cell.setCollege(parts[0]);
            cell.setGrade(parts[1]);
            cell.setCount(e.getValue());
            return cell;
        }).toList();
    }

    private Long collegeIdOf(Long studentId, Map<Long, Student> students,
                             Map<Long, Clazz> clazzes, Map<Long, Major> majors) {
        Student s = students.get(studentId);
        if (s == null || s.getClazzId() == null) {
            return null;
        }
        Clazz cz = clazzes.get(s.getClazzId());
        if (cz == null || cz.getMajorId() == null) {
            return null;
        }
        Major m = majors.get(cz.getMajorId());
        return m == null ? null : m.getCollegeId();
    }

    private List<BigScreenVO.WarningItem> latestWarnings(List<WarningRecord> warnings,
                                                         Map<Long, Student> students) {
        return warnings.stream()
                .filter(w -> "ACTIVE".equals(w.getStatus()))
                .sorted(Comparator.comparing(WarningRecord::getWarningLevel,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WarningRecord::getCreateTime,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LATEST_WARNING_SIZE)
                .map(w -> {
                    BigScreenVO.WarningItem item = new BigScreenVO.WarningItem();
                    item.setWarningNo(w.getWarningNo());
                    Student s = students.get(w.getStudentId());
                    item.setStudentName(s == null ? null : s.getName());
                    item.setTitle(w.getTitle());
                    item.setWarningLevel(w.getWarningLevel());
                    item.setWarningType(w.getWarningType());
                    item.setCreateTime(w.getCreateTime());
                    return item;
                })
                .toList();
    }

    private List<BigScreenVO.TicketItem> latestTickets(Map<Long, Student> students) {
        List<InterventionTicket> tickets = interventionTicketMapper.selectList(
                new LambdaQueryWrapper<InterventionTicket>()
                        .orderByDesc(InterventionTicket::getId)
                        .last("limit " + LATEST_TICKET_SIZE));
        return tickets.stream().map(t -> {
            BigScreenVO.TicketItem item = new BigScreenVO.TicketItem();
            item.setTicketNo(t.getTicketNo());
            Student s = students.get(t.getStudentId());
            item.setStudentName(s == null ? null : s.getName());
            item.setTitle(t.getPlan() == null ? null
                    : (t.getPlan().length() > 40 ? t.getPlan().substring(0, 40) + "…" : t.getPlan()));
            item.setStatus(t.getStatus());
            item.setCreateTime(t.getCreateTime());
            return item;
        }).toList();
    }

    private <E> Map<Long, E> toMap(List<E> list, Function<E, Long> idGetter) {
        return list.stream().filter(e -> idGetter.apply(e) != null)
                .collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }
}
