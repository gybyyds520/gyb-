package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import com.gyb.xqfx.model.vo.MonitorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员监察中心：聚合三端（学生端/教师端/家长端）内容与数据，
 * 支持查看指定学生的数据全景与各端最新动态。
 */
@Service
@RequiredArgsConstructor
public class MonitorService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final StudentParentMapper studentParentMapper;
    private final SysUserMapper sysUserMapper;
    private final LearningGoalMapper goalMapper;
    private final ProfileSnapshotMapper snapshotMapper;
    private final NotifyMessageMapper notifyMapper;
    private final ImMessageMapper imMessageMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final InterventionTicketMapper interventionTicketMapper;
    private final ParentFeedbackMapper parentFeedbackMapper;
    private final ScoreMapper scoreMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final AuditLogMapper auditLogMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final CollegeMapper collegeMapper;

    /** 三端 + 系统概览 */
    public MonitorVO.Overview overview() {
        MonitorVO.Overview o = new MonitorVO.Overview();

        MonitorVO.StudentStat ss = new MonitorVO.StudentStat();
        ss.setStudentCount(studentMapper.selectCount(null));
        ss.setGoalCount(goalMapper.selectCount(null));
        ss.setProfileCount(snapshotMapper.selectCount(null));
        ss.setUnreadNotify(notifyMapper.selectCount(
                new LambdaQueryWrapper<NotifyMessage>().eq(NotifyMessage::getIsRead, 0)));
        ss.setMessageCount(imMessageMapper.selectCount(null));
        o.setStudent(ss);

        MonitorVO.TeacherStat ts = new MonitorVO.TeacherStat();
        ts.setTeacherCount(teacherMapper.selectCount(null));
        ts.setActiveWarning(warningRecordMapper.selectCount(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStatus, "ACTIVE")));
        ts.setOpenTicket(interventionTicketMapper.selectCount(
                new LambdaQueryWrapper<InterventionTicket>().in(InterventionTicket::getStatus, "OPEN", "PROCESSING")));
        ts.setSnapshotCount(snapshotMapper.selectCount(null));
        o.setTeacher(ts);

        MonitorVO.ParentStat ps = new MonitorVO.ParentStat();
        ps.setParentCount(studentParentMapper.selectCount(null));
        ps.setFeedbackCount(parentFeedbackMapper.selectCount(null));
        ps.setUnreadNotify(notifyMapper.selectCount(
                new LambdaQueryWrapper<NotifyMessage>().eq(NotifyMessage::getIsRead, 0)));
        o.setParent(ps);

        MonitorVO.SystemStat sys = new MonitorVO.SystemStat();
        sys.setUserCount(sysUserMapper.selectCount(null));
        sys.setAuditCount(auditLogMapper.selectCount(null));
        sys.setAiCallCount(aiCallLogMapper.selectCount(null));
        sys.setScoreCount(scoreMapper.selectCount(null));
        sys.setPredictionCount(predictionResultMapper.selectCount(null));
        o.setSystem(sys);
        return o;
    }

    /** 各端最新动态 */
    public MonitorVO.Latest latest() {
        MonitorVO.Latest l = new MonitorVO.Latest();
        l.setWarnings(items(warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                        .orderByDesc(WarningRecord::getCreateTime).last("limit 5")),
                w -> studentName(w.getStudentId()) + " · " + w.getTitle(),
                w -> levelText(w.getWarningLevel()) + " " + w.getWarningType(), w -> fmt(w.getCreateTime())));
        l.setTickets(items(interventionTicketMapper.selectList(new LambdaQueryWrapper<InterventionTicket>()
                        .orderByDesc(InterventionTicket::getId).last("limit 5")),
                t -> studentName(t.getStudentId()) + " · " + (t.getPlan() == null ? "" : brief(t.getPlan())),
                t -> t.getStatus(), t -> fmt(t.getCreateTime())));
        l.setFeedbacks(items(parentFeedbackMapper.selectList(new LambdaQueryWrapper<ParentFeedback>()
                        .orderByDesc(ParentFeedback::getId).last("limit 5")),
                f -> studentName(f.getStudentId()) + " 家长回执(" + (f.getAckStatus() == null ? "-" : f.getAckStatus()) + ")",
                f -> brief(f.getContent()), f -> fmt(f.getCreateTime())));
        l.setMessages(items(imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                        .orderByDesc(ImMessage::getId).last("limit 5")),
                m -> "发送人#" + m.getSenderId() + " → 接收人#" + m.getReceiverId(),
                m -> brief(m.getContent()), m -> fmt(m.getCreateTime())));
        l.setAiCalls(items(aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                        .orderByDesc(AiCallLog::getId).last("limit 5")),
                c -> c.getBizType() + " · " + c.getModelName(), c -> "状态 " + c.getStatus(),
                c -> fmt(c.getCreateTime())));
        l.setAudits(items(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                        .orderByDesc(AuditLog::getId).last("limit 5")),
                a -> a.getUsername() + " " + a.getModule() + "·" + a.getOperation(),
                a -> brief(a.getParams()), a -> fmt(a.getCreateTime())));
        return l;
    }

    /** 指定学生数据全景 */
    public MonitorVO.StudentPanorama student(Long studentId) {
        Student s = studentMapper.selectById(studentId);
        MonitorVO.StudentPanorama p = new MonitorVO.StudentPanorama();
        if (s == null) {
            return p;
        }
        p.setStudentId(s.getId());
        p.setStudentNo(s.getStudentNo());
        p.setStudentName(s.getName());
        Clazz cz = s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
        if (cz != null) {
            p.setClazzName(cz.getClazzName());
            p.setGrade(cz.getGrade());
            if (cz.getMajorId() != null) {
                Major m = majorMapper.selectById(cz.getMajorId());
                if (m != null && m.getCollegeId() != null) {
                    College c = collegeMapper.selectById(m.getCollegeId());
                    p.setCollegeName(c == null ? null : c.getCollegeName());
                }
            }
        }
        p.setScoreCount(scoreMapper.selectCount(new LambdaQueryWrapper<Score>().eq(Score::getStudentId, studentId)));
        p.setAvgScore(avgScoreOf(studentId));
        p.setSnapshotCount(snapshotMapper.selectCount(
                new LambdaQueryWrapper<ProfileSnapshot>().eq(ProfileSnapshot::getStudentId, studentId)));
        p.setActiveWarning(warningRecordMapper.selectCount(new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStudentId, studentId).eq(WarningRecord::getStatus, "ACTIVE")));
        p.setGoalCount(goalMapper.selectCount(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getStudentId, studentId)));
        p.setNotifyUnread(notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getIsRead, 0)));
        p.setMessageCount(imMessageMapper.selectCount(null));
        p.setTicketCount(interventionTicketMapper.selectCount(
                new LambdaQueryWrapper<InterventionTicket>().eq(InterventionTicket::getStudentId, studentId)));
        p.setLatestPredictions(items(predictionResultMapper.selectList(new LambdaQueryWrapper<PredictionResult>()
                        .eq(PredictionResult::getStudentId, studentId)
                        .orderByDesc(PredictionResult::getRiskProbability).last("limit 5")),
                pr -> "风险等级 " + pr.getRiskLevel() + " · 概率 "
                        + (pr.getRiskProbability() == null ? "-" : pr.getRiskProbability().toPlainString()),
                pr -> pr.getTermId() == null ? "" : ("学期 " + pr.getTermId()),
                pr -> fmt(pr.getCreateTime())));
        return p;
    }

    // ==================== 内部工具 ====================

    private BigDecimal avgScoreOf(Long studentId) {
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId));
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal sum = scores.stream()
                .map(Score::getTotalScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cnt = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull).count();
        return cnt == 0 ? null : sum.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
    }

    private String studentName(Long studentId) {
        Student s = studentMapper.selectById(studentId);
        return s == null ? ("学生#" + studentId) : s.getName();
    }

    private String levelText(Integer level) {
        return switch (level == null ? 0 : level) {
            case 4 -> "红色";
            case 3 -> "橙色";
            case 2 -> "黄色";
            case 1 -> "蓝色";
            default -> "-";
        };
    }

    private String brief(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 30 ? text.substring(0, 30) + "…" : text;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(TIME_FMT);
    }

    private <E> List<MonitorVO.Item> items(List<E> list, Function<E, String> subject,
                                           Function<E, String> content, Function<E, String> time) {
        return list.stream().map(e -> {
            MonitorVO.Item item = new MonitorVO.Item();
            item.setSubject(subject.apply(e));
            item.setContent(content.apply(e));
            item.setTime(time.apply(e));
            return item;
        }).collect(Collectors.toCollection(ArrayList::new));
    }
}
