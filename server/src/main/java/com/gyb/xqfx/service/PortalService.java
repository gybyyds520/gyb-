package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 学生端门户：学生查看本人学业数据
 *
 * <p>学生只能访问本人数据，不提供他人名单、班级排名明细、
 * 干预工单内部流程与系统管理能力，接口层强制以登录身份取数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalService {

    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final AcademicIndicatorService indicatorService;
    private final DataScopeService dataScopeService;

    /**
     * 当前登录学生的ID，非学生身份或无绑定抛异常
     */
    public Long myStudentId() {
        Long studentId = SecurityUtils.currentStudentId();
        if (studentId == null) {
            studentId = dataScopeService.currentStudentId();
        }
        if (studentId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定学生身份");
        }
        return studentId;
    }

    /**
     * 我的学情总览：学业指标 + 预警数 + 最近成绩
     */
    public MyOverview overview(Long termId) {
        Long studentId = myStudentId();
        Student student = studentMapper.selectById(studentId);

        MyOverview vo = new MyOverview();
        vo.setStudentId(studentId);
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
        }
        AcademicIndicatorVO indicator = indicatorService.calculate(studentId, termId);
        vo.setIndicator(indicator);

        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId));
        vo.setWarningCount(warnings.size());
        vo.setActiveWarningCount((int) warnings.stream()
                .filter(w -> "ACTIVE".equals(w.getStatus())).count());
        // 学生端只展示预警等级、类型与建议行动，不含内部处置信息
        vo.setWarnings(warnings.stream().map(w -> {
            WarningItem item = new WarningItem();
            item.setId(w.getId());
            item.setWarningLevel(w.getWarningLevel());
            item.setWarningType(w.getWarningType());
            item.setStatus(w.getStatus());
            item.setCreateTime(w.getCreateTime());
            item.setAdvice(adviceOf(w.getWarningLevel()));
            return item;
        }).toList());

        // 用可变副本排序，避免查询结果是不可变列表
        List<Score> scores = new ArrayList<>(scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .isNotNull(Score::getTotalScore)));
        scores.sort((a, b) -> {
            if (a.getTermId() == null || b.getTermId() == null) {
                return 0;
            }
            return b.getTermId().compareTo(a.getTermId());
        });
        vo.setRecentScores(scores.stream().limit(10).map(this::toScoreItem).toList());
        vo.setScoreCount(scores.size());
        return vo;
    }

    /**
     * 我的全部成绩
     */
    public List<ScoreItem> myScores(Long termId) {
        Long studentId = myStudentId();
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        return scores.stream().map(this::toScoreItem).toList();
    }

    /**
     * 我的学业指标
     */
    public AcademicIndicatorVO myIndicator(Long termId) {
        return indicatorService.calculate(myStudentId(), termId);
    }

    /**
     * 成绩趋势：按学期汇总平均分、门数、及格率、最高最低分
     *
     * <p>用于多学期对比分析（会员功能）。只统计本人成绩。</p>
     */
    public List<TrendItem> scoreTrend() {
        Long studentId = myStudentId();
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .isNotNull(Score::getTotalScore));

        Map<Long, List<Score>> byTerm = new java.util.LinkedHashMap<>();
        for (Score s : scores) {
            if (s.getTermId() == null) {
                continue;
            }
            byTerm.computeIfAbsent(s.getTermId(), k -> new ArrayList<>()).add(s);
        }

        List<TrendItem> list = new ArrayList<>();
        byTerm.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<Score> items = e.getValue();
                    List<BigDecimal> totals = items.stream().map(Score::getTotalScore).toList();
                    double avg = totals.stream()
                            .mapToDouble(BigDecimal::doubleValue).average().orElse(0);
                    long pass = items.stream().filter(s -> Integer.valueOf(1).equals(s.getIsPass())).count();
                    TrendItem t = new TrendItem();
                    t.setTermId(e.getKey());
                    t.setCourseCount(items.size());
                    t.setAvgScore(BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
                    t.setPassRate(BigDecimal.valueOf(pass * 100.0 / items.size())
                            .setScale(2, java.math.RoundingMode.HALF_UP));
                    t.setMaxScore(totals.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                    t.setMinScore(totals.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                    list.add(t);
                });
        return list;
    }

    private ScoreItem toScoreItem(Score sc) {
        ScoreItem item = new ScoreItem();
        item.setScoreId(sc.getId());
        item.setTermId(sc.getTermId());
        item.setCourseId(sc.getCourseId());
        Course course = sc.getCourseId() == null ? null : courseMapper.selectById(sc.getCourseId());
        item.setCourseName(course == null ? ("课程" + sc.getCourseId()) : course.getCourseName());
        item.setUsualScore(sc.getUsualScore());
        item.setMidScore(sc.getMidScore());
        item.setFinalScore(sc.getFinalScore());
        item.setTotalScore(sc.getTotalScore());
        item.setGradePoint(sc.getGradePoint());
        item.setIsPass(sc.getIsPass());
        return item;
    }

    private String adviceOf(Integer level) {
        return switch (level == null ? 0 : level) {
            case 1 -> "保持当前学习节奏，关注后续课程";
            case 2 -> "建议主动与辅导员沟通，调整学习安排";
            case 3 -> "请尽快联系辅导员，制定学习改进计划";
            case 4 -> "请立即联系辅导员，学校将安排专人帮扶";
            default -> "关注学业状态";
        };
    }

    @Data
    public static class MyOverview {
        private Long studentId;
        private String studentNo;
        private String studentName;
        private AcademicIndicatorVO indicator;
        private Integer warningCount;
        private Integer activeWarningCount;
        private Integer scoreCount;
        private List<WarningItem> warnings = new ArrayList<>();
        private List<ScoreItem> recentScores = new ArrayList<>();
    }

    /** 学期成绩趋势 */
    @Data
    public static class TrendItem {
        private Long termId;
        private Integer courseCount;
        private BigDecimal avgScore;
        private BigDecimal passRate;
        private BigDecimal maxScore;
        private BigDecimal minScore;
    }

    @Data
    public static class WarningItem {
        private Long id;
        private Integer warningLevel;
        private String warningType;
        private String status;
        private java.time.LocalDateTime createTime;
        private String advice;
    }

    @Data
    public static class ScoreItem {
        private Long scoreId;
        private Long termId;
        private Long courseId;
        private String courseName;
        private BigDecimal usualScore;
        private BigDecimal midScore;
        private BigDecimal finalScore;
        private BigDecimal totalScore;
        private BigDecimal gradePoint;
        private Integer isPass;
    }
}
