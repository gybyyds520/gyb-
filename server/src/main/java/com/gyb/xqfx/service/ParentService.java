package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.ParentFeedback;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.ParentFeedbackMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 家长端：查看已绑定子女的学业概况与预警提示，并对帮扶措施回执
 *
 * <p>可见范围严格限定为「本人已绑定的子女」：
 * 不提供其他学生、班级排名、干预工单内部流程、系统管理与数据导出能力。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentService {

    private final StudentParentMapper studentParentMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final ParentFeedbackMapper feedbackMapper;
    private final AcademicIndicatorService indicatorService;

    /**
     * 当前家长已绑定的子女ID列表
     */
    public List<Long> myChildIds() {
        Long userId = SecurityUtils.currentUserId();
        return studentParentMapper.selectList(new LambdaQueryWrapper<StudentParent>()
                        .eq(StudentParent::getParentUserId, userId)
                        .eq(StudentParent::getStatus, 1))
                .stream().map(StudentParent::getStudentId).toList();
    }

    /**
     * 校验子女是否在绑定范围内，否则 403
     */
    public void requireChild(Long studentId) {
        if (studentId == null || !myChildIds().contains(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该学生不在您的监护范围内");
        }
    }

    /**
     * 我的子女列表及学业概况摘要
     */
    public List<ChildBrief> myChildren(Long termId) {
        List<ChildBrief> list = new ArrayList<>();
        for (Long studentId : myChildIds()) {
            Student s = studentMapper.selectById(studentId);
            if (s == null) {
                continue;
            }
            ChildBrief brief = new ChildBrief();
            brief.setStudentId(s.getId());
            brief.setStudentNo(s.getStudentNo());
            brief.setStudentName(s.getName());
            Clazz clazz = s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
            brief.setClazzName(clazz == null ? "未分班" : clazz.getClazzName());

            AcademicIndicatorVO indicator = indicatorService.calculate(studentId, termId);
            brief.setGpa(indicator.getGpa());
            brief.setDeficitCredit(indicator.getDeficitCredit());
            brief.setTrendSlope(indicator.getTrendSlope());
            brief.setRiskFlag(indicator.getRiskFlag());

            List<WarningRecord> warnings = warningRecordMapper.selectList(
                    new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId));
            brief.setActiveWarningCount((int) warnings.stream()
                    .filter(w -> "ACTIVE".equals(w.getStatus())).count());
            brief.setTopWarningLevel(warnings.stream()
                    .filter(w -> "ACTIVE".equals(w.getStatus()))
                    .map(WarningRecord::getWarningLevel)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo).orElse(0));
            list.add(brief);
        }
        return list;
    }

    /**
     * 子女学业详情：指标 + 成绩 + 出勤 + 预警提示
     */
    public ChildDetail childDetail(Long studentId, Long termId) {
        requireChild(studentId);
        Student s = studentMapper.selectById(studentId);
        ChildDetail detail = new ChildDetail();
        detail.setStudentId(studentId);
        if (s != null) {
            detail.setStudentNo(s.getStudentNo());
            detail.setStudentName(s.getName());
            Clazz clazz = s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
            detail.setClazzName(clazz == null ? "未分班" : clazz.getClazzName());
        }
        detail.setIndicator(indicatorService.calculate(studentId, termId));

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        detail.setScoreCount(scores.size());
        detail.setAvgScore(avg(scores.stream().map(Score::getTotalScore).toList()));
        detail.setFailCount((int) scores.stream().filter(sc ->
                sc.getIsPass() != null ? sc.getIsPass() == 0
                        : sc.getTotalScore().compareTo(new BigDecimal("60")) < 0).count());

        List<Attendance> attendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId));
        detail.setAttendanceCount(attendances.size());
        detail.setAbsentCount((int) attendances.stream()
                .filter(a -> "ABSENT".equals(a.getStatus())).count());

        // 家长可见的预警提示：等级、类型、给家长的建议，不含内部处置流程
        detail.setWarnings(warningRecordMapper.selectList(
                        new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId))
                .stream().map(w -> {
                    WarningTip tip = new WarningTip();
                    tip.setId(w.getId());
                    tip.setWarningLevel(w.getWarningLevel());
                    tip.setWarningType(w.getWarningType());
                    tip.setStatus(w.getStatus());
                    tip.setCreateTime(w.getCreateTime());
                    tip.setAdvice(parentAdvice(w.getWarningLevel()));
                    return tip;
                }).toList());

        detail.setFeedbacks(feedbackMapper.selectList(new LambdaQueryWrapper<ParentFeedback>()
                        .eq(ParentFeedback::getStudentId, studentId)
                        .eq(ParentFeedback::getParentId, SecurityUtils.currentUserId()))
                .stream().map(f -> {
                    FeedbackItem item = new FeedbackItem();
                    item.setId(f.getId());
                    item.setBizType(f.getBizType());
                    item.setBizId(f.getBizId());
                    item.setAckStatus(f.getAckStatus());
                    item.setContent(f.getContent());
                    item.setCreateTime(f.getCreateTime());
                    return item;
                }).toList());
        return detail;
    }

    /**
     * 家长对预警/帮扶措施回执
     */
    public Long submitFeedback(Long studentId, String bizType, Long bizId,
                               String ackStatus, String content) {
        requireChild(studentId);
        ParentFeedback fb = new ParentFeedback();
        fb.setStudentId(studentId);
        fb.setParentId(SecurityUtils.currentUserId());
        fb.setBizType(bizType == null ? "WARNING" : bizType);
        fb.setBizId(bizId);
        fb.setAckStatus(ackStatus == null ? "ACKNOWLEDGED" : ackStatus);
        fb.setContent(content);
        fb.setCreateTime(LocalDateTime.now());
        fb.setUpdateTime(LocalDateTime.now());
        fb.setIsDeleted(0);
        feedbackMapper.insert(fb);
        log.info("家长 {} 对学生 {} 提交回执 {}", SecurityUtils.currentUserId(), studentId, ackStatus);
        return fb.getId();
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    private String parentAdvice(Integer level) {
        return switch (level == null ? 0 : level) {
            case 1 -> "孩子状态基本正常，建议保持关注";
            case 2 -> "建议与孩子沟通近期学习情况，必要时联系辅导员";
            case 3 -> "学校已安排关注，请配合与孩子沟通并督促学习";
            case 4 -> "请尽快与辅导员联系，共同帮助孩子调整状态";
            default -> "暂无提示";
        };
    }

    @Data
    public static class ChildBrief {
        private Long studentId;
        private String studentNo;
        private String studentName;
        private String clazzName;
        private BigDecimal gpa;
        private BigDecimal deficitCredit;
        private BigDecimal trendSlope;
        private String riskFlag;
        private Integer activeWarningCount;
        private Integer topWarningLevel;
    }

    @Data
    public static class ChildDetail {
        private Long studentId;
        private String studentNo;
        private String studentName;
        private String clazzName;
        private AcademicIndicatorVO indicator;
        private Integer scoreCount;
        private BigDecimal avgScore;
        private Integer failCount;
        private Integer attendanceCount;
        private Integer absentCount;
        private List<WarningTip> warnings = new ArrayList<>();
        private List<FeedbackItem> feedbacks = new ArrayList<>();
    }

    @Data
    public static class WarningTip {
        private Long id;
        private Integer warningLevel;
        private String warningType;
        private String status;
        private LocalDateTime createTime;
        private String advice;
    }

    @Data
    public static class FeedbackItem {
        private Long id;
        private String bizType;
        private Long bizId;
        private String ackStatus;
        private String content;
        private LocalDateTime createTime;
    }
}
