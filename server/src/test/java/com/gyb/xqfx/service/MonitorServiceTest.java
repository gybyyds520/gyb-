package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
            MajorMapper mam, CollegeMapper com) {
        return new MonitorService(sm, tm, spm, sum, gm, psm, nm, imm, wm, itm,
                pfm, scm, prm, alm, aclm, clm, mam, com);
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
                pfm, scm, prm, alm, aclm, clm, mam, com);
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
                mock(MajorMapper.class), mock(CollegeMapper.class));

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
                mock(AiCallLogMapper.class), clm, mam, com);

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
}
