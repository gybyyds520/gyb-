package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.entity.*;
import com.gyb.xqfx.mapper.*;
import com.gyb.xqfx.model.vo.MonitorVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 监察中心服务测试：三端概览、最新动态与指定学生数据全景
 */
@DisplayName("监察中心服务测试")
class MonitorServiceTest {

    private MonitorService newService(
            StudentMapper sm, TeacherMapper tm, StudentParentMapper spm, SysUserMapper sum,
            LearningGoalMapper gm, ProfileSnapshotMapper psm, NotifyMessageMapper nm,
            ImMessageMapper imm, WarningRecordMapper wm, InterventionTicketMapper itm,
            ParentFeedbackMapper pfm, ScoreMapper scm, PredictionResultMapper prm,
            AuditLogMapper alm, AiCallLogMapper aclm, ClazzMapper clm,
            MajorMapper mam, CollegeMapper com, CourseMapper corm, CourseOfferingMapper cofm,
            AttendanceMapper atm, OnlineBehaviorMapper obm) {
        return new MonitorService(sm, tm, spm, sum, gm, psm, nm, imm, wm, itm,
                pfm, scm, prm, alm, aclm, clm, mam, com, corm, cofm, atm, obm);
    }

    private static <T> T mock(Class<T> clazz) {
        return Mockito.mock(clazz);
    }

    @Test
    @DisplayName("三端与系统概览聚合正确")
    void testOverview() {
        StudentMapper sm = mock(StudentMapper.class);
        TeacherMapper tm = mock(TeacherMapper.class);
        StudentParentMapper spm = mock(StudentParentMapper.class);
        SysUserMapper sum = mock(SysUserMapper.class);
        LearningGoalMapper gm = mock(LearningGoalMapper.class);
        ProfileSnapshotMapper psm = mock(ProfileSnapshotMapper.class);
        NotifyMessageMapper nm = mock(NotifyMessageMapper.class);
        ImMessageMapper imm = mock(ImMessageMapper.class);
        WarningRecordMapper wm = mock(WarningRecordMapper.class);
        InterventionTicketMapper itm = mock(InterventionTicketMapper.class);
        ParentFeedbackMapper pfm = mock(ParentFeedbackMapper.class);
        ScoreMapper scm = mock(ScoreMapper.class);
        PredictionResultMapper prm = mock(PredictionResultMapper.class);
        AuditLogMapper alm = mock(AuditLogMapper.class);
        AiCallLogMapper aclm = mock(AiCallLogMapper.class);
        ClazzMapper clm = mock(ClazzMapper.class);
        MajorMapper mam = mock(MajorMapper.class);
        CollegeMapper com = mock(CollegeMapper.class);

        when(sm.selectCount(null)).thenReturn(100L);
        when(tm.selectCount(null)).thenReturn(20L);
        when(spm.selectCount(null)).thenReturn(30L);
        when(sum.selectCount(null)).thenReturn(160L);
        when(gm.selectCount(null)).thenReturn(45L);
        when(psm.selectCount(null)).thenReturn(500L);
        when(nm.selectCount(any())).thenReturn(12L);
        when(imm.selectCount(null)).thenReturn(80L);
        when(wm.selectCount(any())).thenReturn(8L);
        when(itm.selectCount(any())).thenReturn(3L);
        when(pfm.selectCount(null)).thenReturn(25L);
        when(scm.selectCount(null)).thenReturn(800L);
        when(prm.selectCount(null)).thenReturn(200L);
        when(alm.selectCount(null)).thenReturn(1500L);
        when(aclm.selectCount(null)).thenReturn(60L);

        MonitorService service = newService(sm, tm, spm, sum, gm, psm, nm, imm, wm, itm,
                pfm, scm, prm, alm, aclm, clm, mam, com,
                mock(CourseMapper.class), mock(CourseOfferingMapper.class),
                mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));
        MonitorVO.Overview o = service.overview();

        assertEquals(100L, o.getStudent().getStudentCount());
        assertEquals(45L, o.getStudent().getGoalCount());
        assertEquals(12L, o.getStudent().getUnreadNotify());
        assertEquals(20L, o.getTeacher().getTeacherCount());
        assertEquals(8L, o.getTeacher().getActiveWarning());
        assertEquals(3L, o.getTeacher().getOpenTicket());
        assertEquals(30L, o.getParent().getParentCount());
        assertEquals(25L, o.getParent().getFeedbackCount());
        assertEquals(160L, o.getSystem().getUserCount());
        assertEquals(1500L, o.getSystem().getAuditCount());
        assertEquals(60L, o.getSystem().getAiCallCount());
    }

    @Test
    @DisplayName("最新动态各分组均有数据")
    void testLatest() {
        WarningRecordMapper wm = mock(WarningRecordMapper.class);
        InterventionTicketMapper itm = mock(InterventionTicketMapper.class);
        ParentFeedbackMapper pfm = mock(ParentFeedbackMapper.class);
        ImMessageMapper imm = mock(ImMessageMapper.class);
        AuditLogMapper alm = mock(AuditLogMapper.class);
        AiCallLogMapper aclm = mock(AiCallLogMapper.class);
        StudentMapper sm = mock(StudentMapper.class);
        // 其余 mapper 置空返回
        for (var mapper : new Object[]{mock(TeacherMapper.class), mock(StudentParentMapper.class),
                mock(SysUserMapper.class), mock(LearningGoalMapper.class), mock(ProfileSnapshotMapper.class),
                mock(NotifyMessageMapper.class), mock(ScoreMapper.class), mock(PredictionResultMapper.class),
                mock(ClazzMapper.class), mock(MajorMapper.class), mock(CollegeMapper.class)}) {
            // no-op 仅为消除未使用告警
        }

        WarningRecord w = new WarningRecord();
        w.setId(1L);
        w.setStudentId(7L);
        w.setTitle("成绩波动");
        w.setWarningLevel(3);
        w.setWarningType("ACADEMIC");
        w.setStatus("ACTIVE");
        w.setCreateTime(LocalDateTime.now());
        when(wm.selectList(any())).thenReturn(List.of(w));

        Student s = new Student();
        s.setId(7L);
        s.setName("李四");
        when(sm.selectById(7L)).thenReturn(s);

        when(itm.selectList(any())).thenReturn(List.of(new InterventionTicket()));
        when(pfm.selectList(any())).thenReturn(List.of(new ParentFeedback()));
        when(imm.selectList(any())).thenReturn(List.of(new ImMessage()));
        when(alm.selectList(any())).thenReturn(List.of(new AuditLog()));
        when(aclm.selectList(any())).thenReturn(List.of(new AiCallLog()));

        MonitorService service = newService(sm, mock(TeacherMapper.class), mock(StudentParentMapper.class),
                mock(SysUserMapper.class), mock(LearningGoalMapper.class), mock(ProfileSnapshotMapper.class),
                mock(NotifyMessageMapper.class), imm, wm, itm, pfm, mock(ScoreMapper.class),
                mock(PredictionResultMapper.class), alm, aclm, mock(ClazzMapper.class),
                mock(MajorMapper.class), mock(CollegeMapper.class), mock(CourseMapper.class),
                mock(CourseOfferingMapper.class), mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));

        MonitorVO.Latest l = service.latest();
        assertEquals(1, l.getWarnings().size());
        assertTrue(l.getWarnings().get(0).getSubject().contains("李四"));
        assertEquals(1, l.getTickets().size());
        assertEquals(1, l.getFeedbacks().size());
        assertEquals(1, l.getMessages().size());
        assertEquals(1, l.getAiCalls().size());
        assertEquals(1, l.getAudits().size());
    }

    @Test
    @DisplayName("指定学生全景包含组织链路与成绩均值")
    void testStudentPanorama() {
        StudentMapper sm = mock(StudentMapper.class);
        ClazzMapper clm = mock(ClazzMapper.class);
        MajorMapper mam = mock(MajorMapper.class);
        CollegeMapper com = mock(CollegeMapper.class);
        ScoreMapper scm = mock(ScoreMapper.class);
        WarningRecordMapper wm = mock(WarningRecordMapper.class);
        LearningGoalMapper gm = mock(LearningGoalMapper.class);
        ProfileSnapshotMapper psm = mock(ProfileSnapshotMapper.class);
        NotifyMessageMapper nm = mock(NotifyMessageMapper.class);
        InterventionTicketMapper itm = mock(InterventionTicketMapper.class);
        PredictionResultMapper prm = mock(PredictionResultMapper.class);

        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("20230101");
        s.setName("张三");
        s.setClazzId(11L);
        when(sm.selectById(1L)).thenReturn(s);

        Clazz cz = new Clazz();
        cz.setId(11L);
        cz.setClazzName("计科2301");
        cz.setGrade("2023");
        cz.setMajorId(21L);
        when(clm.selectById(11L)).thenReturn(cz);

        Major mj = new Major();
        mj.setId(21L);
        mj.setCollegeId(31L);
        when(mam.selectById(21L)).thenReturn(mj);

        College c = new College();
        c.setId(31L);
        c.setCollegeName("计算与信息科学学院");
        when(com.selectById(31L)).thenReturn(c);

        Score s1 = new Score();
        s1.setStudentId(1L);
        s1.setTotalScore(new BigDecimal("80"));
        Score s2 = new Score();
        s2.setStudentId(1L);
        s2.setTotalScore(new BigDecimal("90"));
        when(scm.selectList(any())).thenReturn(List.of(s1, s2));
        when(scm.selectCount(any())).thenReturn(2L);
        when(wm.selectCount(any())).thenReturn(2L);
        when(gm.selectCount(any())).thenReturn(3L);
        when(psm.selectCount(any())).thenReturn(6L);
        when(nm.selectCount(any())).thenReturn(1L);
        when(itm.selectCount(any())).thenReturn(0L);

        PredictionResult pr = new PredictionResult();
        pr.setStudentId(1L);
        pr.setRiskLevel(4);
        pr.setRiskProbability(new BigDecimal("0.85"));
        when(prm.selectList(any())).thenReturn(List.of(pr));

        MonitorService service = newService(sm, mock(TeacherMapper.class), mock(StudentParentMapper.class),
                mock(SysUserMapper.class), gm, psm, nm, mock(ImMessageMapper.class), wm, itm,
                mock(ParentFeedbackMapper.class), scm, prm, mock(AuditLogMapper.class),
                mock(AiCallLogMapper.class), clm, mam, com,
                mock(CourseMapper.class), mock(CourseOfferingMapper.class),
                mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));

        MonitorVO.StudentPanorama p = service.student(1L);

        assertEquals("张三", p.getStudentName());
        assertEquals("计科2301", p.getClazzName());
        assertEquals("计算与信息科学学院", p.getCollegeName());
        assertEquals(2L, p.getScoreCount());
        assertEquals(0, new BigDecimal("85.00").compareTo(p.getAvgScore()));
        assertEquals(2L, p.getActiveWarning());
        assertEquals(3L, p.getGoalCount());
        assertEquals(1, p.getLatestPredictions().size());
        assertTrue(p.getLatestPredictions().get(0).getSubject().contains("0.85"));
    }

    @Test
    @DisplayName("全量记录分页：预警与成绩维度映射正确")
    void testRecords() {
        WarningRecordMapper wm = mock(WarningRecordMapper.class);
        ScoreMapper scm = mock(ScoreMapper.class);
        CourseMapper corm = mock(CourseMapper.class);
        StudentMapper sm = mock(StudentMapper.class);

        WarningRecord w = new WarningRecord();
        w.setId(5L);
        w.setWarningNo("W2026101800005");
        w.setStudentId(7L);
        w.setTitle("考勤异常");
        w.setWarningLevel(2);
        w.setWarningType("ATTENDANCE");
        w.setStatus("ACTIVE");
        w.setCreateTime(LocalDateTime.now());
        Page<WarningRecord> wp = new Page<>(1, 10);
        wp.setTotal(1);
        wp.setRecords(List.of(w));
        when(wm.selectPage(any(), any())).thenReturn(wp);

        Student s = new Student();
        s.setId(7L);
        s.setName("李四");
        when(sm.selectById(7L)).thenReturn(s);

        Score sc = new Score();
        sc.setId(3L);
        sc.setStudentId(7L);
        sc.setCourseId(21L);
        sc.setTotalScore(new BigDecimal("88"));
        Page<Score> sp = new Page<>(1, 10);
        sp.setTotal(1);
        sp.setRecords(List.of(sc));
        when(scm.selectPage(any(), any())).thenReturn(sp);
        Course c = new Course();
        c.setId(21L);
        c.setCourseName("数据结构");
        when(corm.selectById(21L)).thenReturn(c);

        MonitorService service = newService(sm, mock(TeacherMapper.class), mock(StudentParentMapper.class),
                mock(SysUserMapper.class), mock(LearningGoalMapper.class), mock(ProfileSnapshotMapper.class),
                mock(NotifyMessageMapper.class), mock(ImMessageMapper.class), wm, mock(InterventionTicketMapper.class),
                mock(ParentFeedbackMapper.class), scm, mock(PredictionResultMapper.class),
                mock(AuditLogMapper.class), mock(AiCallLogMapper.class), mock(ClazzMapper.class),
                mock(MajorMapper.class), mock(CollegeMapper.class), corm, mock(CourseOfferingMapper.class),
                mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));

        PageResult<MonitorVO.RecordRow> warnings = service.records("warning", 1, 10);
        assertEquals(1, warnings.getRecords().size());
        MonitorVO.RecordRow r = warnings.getRecords().get(0);
        assertEquals("W2026101800005", r.getBizId());
        assertTrue(r.getSubject().contains("李四"));
        assertEquals("黄色 ATTENDANCE", r.getContent());

        PageResult<MonitorVO.RecordRow> scores = service.records("score", 1, 10);
        assertTrue(scores.getRecords().get(0).getSubject().contains("数据结构"));
        assertEquals("88", scores.getRecords().get(0).getContent());
    }

    @Test
    @DisplayName("三端账号列表含组织信息")
    void testAccounts() {
        StudentMapper sm = mock(StudentMapper.class);
        ClazzMapper clm = mock(ClazzMapper.class);
        StudentParentMapper spm = mock(StudentParentMapper.class);

        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("20230101");
        s.setName("张三");
        s.setClazzId(11L);
        when(sm.selectList(null)).thenReturn(List.of(s));
        Clazz cz = new Clazz();
        cz.setClazzName("计科2301");
        cz.setGrade("2023");
        when(clm.selectById(11L)).thenReturn(cz);

        StudentParent bind = new StudentParent();
        bind.setParentUserId(100L);
        bind.setStudentId(1L);
        bind.setParentName("张三家长");
        bind.setRelation("FATHER");
        when(spm.selectList(any())).thenReturn(List.of(bind));

        MonitorService service = newService(sm, mock(TeacherMapper.class), spm, mock(SysUserMapper.class),
                mock(LearningGoalMapper.class), mock(ProfileSnapshotMapper.class), mock(NotifyMessageMapper.class),
                mock(ImMessageMapper.class), mock(WarningRecordMapper.class), mock(InterventionTicketMapper.class),
                mock(ParentFeedbackMapper.class), mock(ScoreMapper.class), mock(PredictionResultMapper.class),
                mock(AuditLogMapper.class), mock(AiCallLogMapper.class), clm, mock(MajorMapper.class),
                mock(CollegeMapper.class), mock(CourseMapper.class), mock(CourseOfferingMapper.class),
                mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));

        List<MonitorVO.Account> students = service.accounts("STUDENT");
        assertEquals(1, students.size());
        assertEquals("张三", students.get(0).getName());
        assertTrue(students.get(0).getExtra().contains("计科2301"));

        List<MonitorVO.Account> parents = service.accounts("PARENT");
        assertEquals(1, parents.size());
        assertEquals(100L, parents.get(0).getId());
        assertTrue(parents.get(0).getExtra().contains("张三"));
    }

    @Test
    @DisplayName("任意账号数据全景：学生/教师/家长三端")
    void testAccountDetail() {
        SysUserMapper sum = mock(SysUserMapper.class);
        StudentMapper sm = mock(StudentMapper.class);
        ClazzMapper clm = mock(ClazzMapper.class);
        MajorMapper mam = mock(MajorMapper.class);
        CollegeMapper com = mock(CollegeMapper.class);
        ScoreMapper scm = mock(ScoreMapper.class);
        WarningRecordMapper wm = mock(WarningRecordMapper.class);
        LearningGoalMapper gm = mock(LearningGoalMapper.class);
        ProfileSnapshotMapper psm = mock(ProfileSnapshotMapper.class);
        NotifyMessageMapper nm = mock(NotifyMessageMapper.class);
        InterventionTicketMapper itm = mock(InterventionTicketMapper.class);
        PredictionResultMapper prm = mock(PredictionResultMapper.class);
        TeacherMapper tm = mock(TeacherMapper.class);
        CourseOfferingMapper cofm = mock(CourseOfferingMapper.class);
        StudentParentMapper spm = mock(StudentParentMapper.class);
        ParentFeedbackMapper pfm = mock(ParentFeedbackMapper.class);
        AttendanceMapper atm = mock(AttendanceMapper.class);
        OnlineBehaviorMapper obm = mock(OnlineBehaviorMapper.class);

        // 学生账号
        SysUser su = new SysUser();
        su.setId(100L);
        su.setUsername("stu001");
        su.setRealName("张三");
        su.setUserType("STUDENT");
        su.setStudentId(1L);
        when(sum.selectById(100L)).thenReturn(su);

        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("20230101");
        s.setName("张三");
        s.setClazzId(11L);
        when(sm.selectById(1L)).thenReturn(s);
        Clazz cz = new Clazz();
        cz.setClazzName("计科2301");
        cz.setGrade("2023");
        cz.setMajorId(21L);
        when(clm.selectById(11L)).thenReturn(cz);
        Major mj = new Major();
        mj.setId(21L);
        mj.setCollegeId(31L);
        when(mam.selectById(21L)).thenReturn(mj);
        College c = new College();
        c.setId(31L);
        c.setCollegeName("计算与信息科学学院");
        when(com.selectById(31L)).thenReturn(c);
        when(scm.selectList(any())).thenReturn(List.of());
        when(scm.selectCount(any())).thenReturn(0L);
        when(psm.selectCount(any())).thenReturn(0L);
        when(gm.selectCount(any())).thenReturn(0L);
        when(nm.selectCount(any())).thenReturn(0L);
        when(itm.selectCount(any())).thenReturn(0L);
        when(atm.selectCount(any())).thenReturn(0L);
        when(obm.selectCount(any())).thenReturn(0L);
        when(wm.selectCount(any())).thenReturn(0L);
        when(prm.selectList(any())).thenReturn(List.of());

        MonitorService service = newService(sm, tm, spm, sum, gm, psm, nm,
                mock(ImMessageMapper.class), wm, itm, pfm, scm, prm, mock(AuditLogMapper.class),
                mock(AiCallLogMapper.class), clm, mam, com, mock(CourseMapper.class), cofm, atm, obm);

        MonitorVO.AccountDetail stu = service.accountDetail(100L);
        assertEquals("STUDENT", stu.getRole());
        assertEquals("张三", stu.getStudent().getStudentName());

        // 教师账号
        SysUser tu = new SysUser();
        tu.setId(101L);
        tu.setUsername("tea001");
        tu.setRealName("王老师");
        tu.setUserType("TEACHER");
        tu.setTeacherId(9L);
        when(sum.selectById(101L)).thenReturn(tu);
        Teacher t = new Teacher();
        t.setId(9L);
        t.setName("王老师");
        t.setTeacherNo("T009");
        when(tm.selectById(9L)).thenReturn(t);
        CourseOffering off = new CourseOffering();
        off.setId(1L);
        off.setTeacherId(9L);
        off.setClazzId(11L);
        when(cofm.selectList(any())).thenReturn(List.of(off));
        when(sm.selectList(any())).thenReturn(List.of(s));

        MonitorVO.AccountDetail tea = service.accountDetail(101L);
        assertEquals("TEACHER", tea.getRole());
        assertEquals(1, tea.getTeacherStudents().size());
        assertTrue(tea.getTeacherStudents().get(0).getSubject().contains("张三"));

        // 家长账号
        SysUser pu = new SysUser();
        pu.setId(102L);
        pu.setUsername("parent001");
        pu.setRealName("张三家长");
        pu.setUserType("PARENT");
        when(sum.selectById(102L)).thenReturn(pu);
        StudentParent bind = new StudentParent();
        bind.setParentUserId(102L);
        bind.setStudentId(1L);
        bind.setParentName("张三家长");
        bind.setRelation("FATHER");
        when(spm.selectList(any())).thenReturn(List.of(bind));
        when(pfm.selectCount(any())).thenReturn(2L);

        MonitorVO.AccountDetail par = service.accountDetail(102L);
        assertEquals("PARENT", par.getRole());
        assertEquals(1, par.getParentChildren().size());
        assertTrue(par.getParentChildren().get(0).getSubject().contains("张三"));
        assertEquals(2L, par.getStat().getFeedbackCount());
    }

    @Test
    @DisplayName("管理员删除三端账号：学生/家长可删，管理岗受保护")
    void testDeleteAccount() {
        SysUserMapper sum = mock(SysUserMapper.class);
        StudentMapper sm = mock(StudentMapper.class);
        TeacherMapper tm = mock(TeacherMapper.class);
        StudentParentMapper spm = mock(StudentParentMapper.class);

        SysUser stu = new SysUser();
        stu.setId(200L);
        stu.setUsername("stu200");
        stu.setRealName("学生甲");
        stu.setUserType("STUDENT");
        stu.setStudentId(5L);
        when(sum.selectById(200L)).thenReturn(stu);

        SysUser par = new SysUser();
        par.setId(201L);
        par.setUsername("par201");
        par.setRealName("家长甲");
        par.setUserType("PARENT");
        when(sum.selectById(201L)).thenReturn(par);

        SysUser adm = new SysUser();
        adm.setId(1L);
        adm.setUsername("admin");
        adm.setUserType("ADMIN");
        when(sum.selectById(1L)).thenReturn(adm);

        MonitorService service = newService(sm, tm, spm, sum, mock(LearningGoalMapper.class),
                mock(ProfileSnapshotMapper.class), mock(NotifyMessageMapper.class), mock(ImMessageMapper.class),
                mock(WarningRecordMapper.class), mock(InterventionTicketMapper.class), mock(ParentFeedbackMapper.class),
                mock(ScoreMapper.class), mock(PredictionResultMapper.class), mock(AuditLogMapper.class),
                mock(AiCallLogMapper.class), mock(ClazzMapper.class), mock(MajorMapper.class),
                mock(CollegeMapper.class), mock(CourseMapper.class), mock(CourseOfferingMapper.class),
                mock(AttendanceMapper.class), mock(OnlineBehaviorMapper.class));

        // 删除学生账号：级联学生记录与家长绑定
        String r1 = service.deleteAccount(200L);
        assertTrue(r1.contains("学生甲"));
        verify(sm).deleteById(5L);
        verify(spm).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sum).deleteById(200L);

        // 删除家长账号：清理绑定并删登录账号
        String r2 = service.deleteAccount(201L);
        assertTrue(r2.contains("家长甲"));
        verify(spm, org.mockito.Mockito.times(2)).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sum).deleteById(201L);

        // 管理岗账号受保护
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(1L));
        verify(sum, org.mockito.Mockito.never()).deleteById(1L);
    }
}
