package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import com.gyb.xqfx.model.vo.MonitorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private final CourseMapper courseMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final AttendanceMapper attendanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;

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
        p.setAttendanceCount(attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId)));
        p.setBehaviorCount(onlineBehaviorMapper.selectCount(
                new LambdaQueryWrapper<OnlineBehavior>().eq(OnlineBehavior::getStudentId, studentId)));
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

    /**
     * 全量记录分页：管理员可查看任意端任意维度的全部数据（至高无上权限）。
     */
    public PageResult<MonitorVO.RecordRow> records(String dimension, long current, long size) {
        String dim = dimension == null ? "warning" : dimension;
        return switch (dim) {
            case "warning" -> pageRows(warningRecordMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<WarningRecord>().orderByDesc(WarningRecord::getId)),
                    w -> row(w.getId(), w.getWarningNo(), studentName(w.getStudentId()) + " · " + w.getTitle(),
                            levelText(w.getWarningLevel()) + " " + w.getWarningType(), w.getStatus(),
                            fmt(w.getCreateTime())));
            case "ticket" -> pageRows(interventionTicketMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<InterventionTicket>().orderByDesc(InterventionTicket::getId)),
                    t -> row(t.getId(), t.getTicketNo(), studentName(t.getStudentId()) + " · " + brief(t.getPlan()),
                            t.getStatus(), "", fmt(t.getCreateTime())));
            case "feedback" -> pageRows(parentFeedbackMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<ParentFeedback>().orderByDesc(ParentFeedback::getId)),
                    f -> row(f.getId(), f.getBizType() + "#" + f.getBizId(), studentName(f.getStudentId()) + " 家长回执",
                            f.getAckStatus(), brief(f.getContent()), fmt(f.getCreateTime())));
            case "message" -> pageRows(imMessageMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<ImMessage>().orderByDesc(ImMessage::getId)),
                    m -> row(m.getId(), null, "发送人#" + m.getSenderId() + " → 接收人#" + m.getReceiverId(),
                            m.getIsRead() != null && m.getIsRead() == 1 ? "已读" : "未读", brief(m.getContent()),
                            fmt(m.getCreateTime())));
            case "notify" -> pageRows(notifyMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<NotifyMessage>().orderByDesc(NotifyMessage::getId)),
                    n -> row(n.getId(), n.getBizType() + "#" + n.getBizId(), n.getTitle(),
                            n.getMessageType(), brief(n.getContent()), fmt(n.getCreateTime())));
            case "ai" -> pageRows(aiCallLogMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<AiCallLog>().orderByDesc(AiCallLog::getId)),
                    c -> row(c.getId(), null, c.getBizType() + " · " + c.getModelName(),
                            c.getStatus(), c.getTotalTokens() == null ? "" : (c.getTotalTokens() + " tokens"),
                            fmt(c.getCreateTime())));
            case "audit" -> pageRows(auditLogMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId)),
                    a -> row(a.getId(), null, a.getUsername() + " " + a.getModule() + "·" + a.getOperation(),
                            a.getSuccess() != null && a.getSuccess() == 1 ? "成功" : "失败",
                            a.getCostMs() == null ? "" : (a.getCostMs() + "ms"), fmt(a.getCreateTime())));
            case "goal" -> pageRows(goalMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<LearningGoal>().orderByDesc(LearningGoal::getId)),
                    g -> row(g.getId(), g.getGoalType(), studentName(g.getStudentId()) + " · " + g.getGoalType() + "目标",
                            g.getStatus(), g.getTargetValue() == null ? "" : ("目标 " + g.getTargetValue().toPlainString()),
                            fmt(g.getCreateTime())));
            case "prediction" -> pageRows(predictionResultMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<PredictionResult>().orderByDesc(PredictionResult::getId)),
                    p -> row(p.getId(), null, studentName(p.getStudentId()) + " 风险" + p.getRiskLevel(),
                            p.getRiskProbability() == null ? "" : ("概率 " + p.getRiskProbability().toPlainString()),
                            p.getTermId() == null ? "" : ("学期 " + p.getTermId()), fmt(p.getCreateTime())));
            case "score" -> pageRows(scoreMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<Score>().orderByDesc(Score::getId)),
                    s -> row(s.getId(), null, studentName(s.getStudentId()) + " · " + courseName(s.getCourseId()),
                            s.getTotalScore() == null ? "" : s.getTotalScore().toPlainString(),
                            s.getExamStatus() == null ? "" : s.getExamStatus(), fmt(s.getCreateTime())));
            case "attendance" -> pageRows(attendanceMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<Attendance>().orderByDesc(Attendance::getId)),
                    a -> row(a.getId(), null, studentName(a.getStudentId()) + " " + a.getPeriod(),
                            a.getStatus(), a.getCheckDate() == null ? "" : a.getCheckDate().toString(),
                            fmt(a.getCreateTime())));
            case "behavior" -> pageRows(onlineBehaviorMapper.selectPage(new Page<>(current, size),
                            new LambdaQueryWrapper<OnlineBehavior>().orderByDesc(OnlineBehavior::getId)),
                    b -> row(b.getId(), null, studentName(b.getStudentId()) + " · 登录" + b.getLoginCount()
                            + " · 学习" + b.getStudyMinutes() + "min", "",
                            b.getVideoProgress() == null ? "" : ("进度 " + b.getVideoProgress().toPlainString() + "%"),
                            fmt(b.getCreateTime())));
            default -> new PageResult<>();
        };
    }

    /**
     * 三端全部账号（学生端/教师端/家长端），供管理员选择任意账号查看全景。
     */
    public List<MonitorVO.Account> accounts(String role) {
        String r = role == null ? "STUDENT" : role;
        return switch (r) {
            case "TEACHER" -> teacherMapper.selectList(null).stream().map(t -> {
                MonitorVO.Account a = new MonitorVO.Account();
                a.setId(t.getId());
                a.setUserId(t.getUserId());
                a.setNo(t.getTeacherNo());
                a.setName(t.getName());
                a.setExtra("教师");
                return a;
            }).collect(Collectors.toList());
            case "PARENT" -> {
                Map<Long, String> studentName = studentNameMap();
                yield studentParentMapper.selectList(
                                new LambdaQueryWrapper<StudentParent>().orderByAsc(StudentParent::getId)).stream()
                        .collect(Collectors.toMap(StudentParent::getParentUserId, Function.identity(), (a, b) -> a))
                        .values().stream()
                        .map(p -> {
                            MonitorVO.Account a = new MonitorVO.Account();
                            a.setId(p.getParentUserId());
                            a.setUserId(p.getParentUserId());
                            a.setNo("");
                            a.setName(p.getParentName() == null ? ("家长#" + p.getParentUserId()) : p.getParentName());
                            a.setExtra((p.getRelation() == null ? "" : p.getRelation())
                                    + (studentName.get(p.getStudentId()) == null ? ""
                                    : (" · " + studentName.get(p.getStudentId()))));
                            return a;
                        }).collect(Collectors.toList());
            }
            default -> studentMapper.selectList(null).stream().map(s -> {
                MonitorVO.Account a = new MonitorVO.Account();
                a.setId(s.getId());
                a.setUserId(s.getUserId());
                a.setNo(s.getStudentNo());
                a.setName(s.getName());
                Clazz cz = s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
                a.setExtra(cz == null ? "" : (cz.getClazzName() + (cz.getGrade() == null ? "" : " · " + cz.getGrade())));
                return a;
            }).collect(Collectors.toList());
        };
    }

    /**
     * 任意账号数据全景：管理员以至高无上权限查看该账号的全部信息与功能。
     */
    public MonitorVO.AccountDetail accountDetail(Long id) {
        MonitorVO.AccountDetail d = new MonitorVO.AccountDetail();
        d.setId(id);
        SysUser u = sysUserMapper.selectById(id);
        if (u != null) {
            d.setName(u.getRealName() == null ? u.getUsername() : u.getRealName());
            d.setNo(u.getUsername());
            d.setRole(u.getUserType());
            if ("STUDENT".equals(u.getUserType()) && u.getStudentId() != null) {
                fillStudentDetail(d, u.getStudentId());
                return d;
            }
            if ("TEACHER".equals(u.getUserType()) && u.getTeacherId() != null) {
                fillTeacherDetail(d, u.getTeacherId());
                return d;
            }
            if ("PARENT".equals(u.getUserType())) {
                fillParentDetail(d, u.getId());
                return d;
            }
            // 管理岗账号：直接给系统概览信息
            MonitorVO.AccountStat st = new MonitorVO.AccountStat();
            st.setActiveWarning(warningRecordMapper.selectCount(
                    new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStatus, "ACTIVE")));
            d.setStat(st);
            return d;
        }
        // 非 sys_user 主键时，尝试按业务表 id 兜底
        Student s = studentMapper.selectById(id);
        if (s != null) {
            fillStudentDetail(d, id);
            return d;
        }
        return d;
    }

    // ==================== 全景填充 ====================

    private void fillStudentDetail(MonitorVO.AccountDetail d, Long studentId) {
        d.setRole("STUDENT");
        MonitorVO.StudentPanorama p = student(studentId);
        d.setStudent(p);
        if (d.getName() == null) {
            d.setName(p.getStudentName());
            d.setNo(p.getStudentNo());
        }
        d.setExtra(p.getClazzName() + (p.getGrade() == null ? "" : (" · " + p.getGrade()))
                + (p.getCollegeName() == null ? "" : (" · " + p.getCollegeName())));
    }

    private void fillTeacherDetail(MonitorVO.AccountDetail d, Long teacherId) {
        Teacher t = teacherMapper.selectById(teacherId);
        if (t != null) {
            if (d.getName() == null) {
                d.setName(t.getName());
                d.setNo(t.getTeacherNo());
            }
            d.setExtra("教师");
        }
        // 名下开课班级 → 名下学生
        List<CourseOffering> offerings = courseOfferingMapper.selectList(
                new LambdaQueryWrapper<CourseOffering>().eq(CourseOffering::getTeacherId, teacherId));
        Set<Long> clazzIds = offerings.stream().map(CourseOffering::getClazzId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Student> students = clazzIds.isEmpty() ? List.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getClazzId, clazzIds));
        d.setTeacherStudents(students.stream().map(s -> {
            MonitorVO.Item item = new MonitorVO.Item();
            item.setSubject(s.getStudentNo() + " " + s.getName());
            item.setContent("预警 "
                    + warningRecordMapper.selectCount(new LambdaQueryWrapper<WarningRecord>()
                    .eq(WarningRecord::getStudentId, s.getId()).eq(WarningRecord::getStatus, "ACTIVE")) + " 条");
            return item;
        }).collect(Collectors.toList()));

        MonitorVO.AccountStat st = new MonitorVO.AccountStat();
        Set<Long> studentIds = students.stream().map(Student::getId).collect(Collectors.toSet());
        st.setActiveWarning(studentIds.isEmpty() ? 0L
                : warningRecordMapper.selectCount(new LambdaQueryWrapper<WarningRecord>()
                .in(WarningRecord::getStudentId, studentIds).eq(WarningRecord::getStatus, "ACTIVE")));
        st.setTicketCount(studentIds.isEmpty() ? 0L
                : interventionTicketMapper.selectCount(new LambdaQueryWrapper<InterventionTicket>()
                .in(InterventionTicket::getStudentId, studentIds)));
        d.setStat(st);
    }

    private void fillParentDetail(MonitorVO.AccountDetail d, Long parentUserId) {
        d.setRole("PARENT");
        List<StudentParent> binds = studentParentMapper.selectList(
                new LambdaQueryWrapper<StudentParent>().eq(StudentParent::getParentUserId, parentUserId));
        long childWarnings = 0L;
        List<MonitorVO.Item> children = new ArrayList<>();
        for (StudentParent b : binds) {
            Student s = studentMapper.selectById(b.getStudentId());
            if (s == null) {
                continue;
            }
            long act = warningRecordMapper.selectCount(new LambdaQueryWrapper<WarningRecord>()
                    .eq(WarningRecord::getStudentId, s.getId()).eq(WarningRecord::getStatus, "ACTIVE"));
            childWarnings += act;
            MonitorVO.Item item = new MonitorVO.Item();
            item.setSubject(s.getStudentNo() + " " + s.getName());
            item.setContent("待处理预警 " + act + " 条");
            item.setExtra(b.getRelation() == null ? "" : b.getRelation());
            children.add(item);
        }
        d.setParentChildren(children);
        if (d.getName() == null) {
            d.setName(children.isEmpty() ? ("家长#" + parentUserId)
                    : (binds.get(0).getParentName() == null ? ("家长#" + parentUserId) : binds.get(0).getParentName()));
        }
        MonitorVO.AccountStat st = new MonitorVO.AccountStat();
        st.setActiveWarning(childWarnings);
        st.setFeedbackCount(parentFeedbackMapper.selectCount(
                new LambdaQueryWrapper<ParentFeedback>().eq(ParentFeedback::getParentId, parentUserId)));
        st.setNotifyUnread(notifyMapper.selectCount(
                new LambdaQueryWrapper<NotifyMessage>().eq(NotifyMessage::getUserId, parentUserId)
                        .eq(NotifyMessage::getIsRead, 0)));
        d.setStat(st);
    }

    // ==================== 工具 ====================

    private <E> PageResult<MonitorVO.RecordRow> pageRows(IPage<E> page, Function<E, MonitorVO.RecordRow> converter) {
        return PageResult.of(page, converter);
    }

    private MonitorVO.RecordRow row(Long id, String bizId, String subject, String content, String extra, String time) {
        MonitorVO.RecordRow r = new MonitorVO.RecordRow();
        r.setId(id);
        r.setBizId(bizId);
        r.setSubject(subject);
        r.setContent(content);
        r.setExtra(extra);
        r.setTime(time);
        return r;
    }

    private String courseName(Long courseId) {
        if (courseId == null) {
            return "";
        }
        Course c = courseMapper.selectById(courseId);
        return c == null ? ("课程#" + courseId) : c.getCourseName();
    }

    private Map<Long, String> clazzNameMap() {
        return clazzMapper.selectList(null).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getClazzName, (a, b) -> a));
    }

    private Map<Long, String> studentNameMap() {
        return studentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
    }

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
