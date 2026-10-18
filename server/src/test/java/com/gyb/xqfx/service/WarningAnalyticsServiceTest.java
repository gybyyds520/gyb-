package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.College;
import com.gyb.xqfx.entity.InterventionLog;
import com.gyb.xqfx.entity.InterventionTicket;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 预警分析服务测试（下钻 / 时间线 / 学业触发）
 */
@DisplayName("预警分析服务测试")
class WarningAnalyticsServiceTest {

    private WarningRecordMapper warningRecordMapper;
    private StudentMapper studentMapper;
    private CollegeMapper collegeMapper;
    private MajorMapper majorMapper;
    private ClazzMapper clazzMapper;
    private ScoreMapper scoreMapper;
    private AttendanceMapper attendanceMapper;
    private InterventionTicketMapper ticketMapper;
    private InterventionLogMapper logMapper;
    private AcademicIndicatorService indicatorService;
    private WarningAnalyticsService service;

    @BeforeEach
    void setUp() {
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        collegeMapper = Mockito.mock(CollegeMapper.class);
        majorMapper = Mockito.mock(MajorMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        attendanceMapper = Mockito.mock(AttendanceMapper.class);
        ticketMapper = Mockito.mock(InterventionTicketMapper.class);
        logMapper = Mockito.mock(InterventionLogMapper.class);
        indicatorService = Mockito.mock(AcademicIndicatorService.class);
        service = new WarningAnalyticsService(warningRecordMapper, studentMapper, collegeMapper,
                majorMapper, clazzMapper, scoreMapper, attendanceMapper, ticketMapper, logMapper,
                indicatorService);
    }

    private Student student(Long id, Long collegeId, String grade, String gender) {
        Student s = new Student();
        s.setId(id);
        s.setCollegeId(collegeId);
        s.setGrade(grade);
        s.setGender(gender);
        return s;
    }

    private WarningRecord warning(Long studentId, Integer level, String status) {
        WarningRecord w = new WarningRecord();
        w.setId(studentId);
        w.setStudentId(studentId);
        w.setWarningLevel(level);
        w.setStatus(status);
        w.setCreateTime(LocalDateTime.now());
        return w;
    }

    // ==================== 多维下钻 ====================

    @Test
    @DisplayName("按学院下钻统计预警数与预警率")
    void testDrillByCollege() {
        College c1 = new College();
        c1.setId(1L);
        c1.setCollegeName("计算机学院");
        College c2 = new College();
        c2.setId(2L);
        c2.setCollegeName("数学学院");
        Mockito.when(collegeMapper.selectById(1L)).thenReturn(c1);
        Mockito.when(collegeMapper.selectById(2L)).thenReturn(c2);
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1L, 1L, "2021", "M"), student(2L, 1L, "2021", "F"), student(3L, 2L, "2021", "M")));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(
                warning(1L, 4, "ACTIVE"), warning(1L, 2, "HANDLED")));

        List<WarningAnalyticsVO.DrillItem> items = service.drillDown(null, "COLLEGE", null);
        assertEquals(2, items.size());
        // 计算机学院预警 2 条排前
        assertEquals("计算机学院", items.get(0).getName());
        assertEquals(2, items.get(0).getStudentCount());
        assertEquals(2, items.get(0).getWarningCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(items.get(0).getWarningRate()));
        assertEquals(1, items.get(0).getRedCount());
        assertEquals(1, items.get(0).getYellowCount());
        assertEquals(1, items.get(0).getHandledCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(items.get(0).getHandleRate()));
        assertEquals("数学学院", items.get(1).getName());
        assertEquals(0, items.get(1).getWarningCount());
    }

    @Test
    @DisplayName("按年级下钻")
    void testDrillByGrade() {
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1L, 1L, "2021", "M"), student(2L, 1L, "2022", "F")));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());

        List<WarningAnalyticsVO.DrillItem> items = service.drillDown(null, "GRADE", null);
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(i -> "2021级".equals(i.getName())));
        assertTrue(items.stream().anyMatch(i -> "2022级".equals(i.getName())));
    }

    @Test
    @DisplayName("按性别下钻并归一化显示")
    void testDrillByGender() {
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(
                student(1L, 1L, "2021", "M"), student(2L, 1L, "2021", "F"),
                student(3L, 1L, "2021", "男")));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());

        List<WarningAnalyticsVO.DrillItem> items = service.drillDown(null, "GENDER", null);
        // M 与 男 归一化为同一项
        assertTrue(items.stream().anyMatch(i -> "男".equals(i.getName()) && i.getStudentCount() == 2));
        assertTrue(items.stream().anyMatch(i -> "女".equals(i.getName()) && i.getStudentCount() == 1));
    }

    @Test
    @DisplayName("未分配学院归入默认分组")
    void testDrillUnassigned() {
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(student(1L, null, "2021", "M")));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());
        assertEquals("未分配学院", service.drillDown(null, "COLLEGE", null).get(0).getName());
    }

    @Test
    @DisplayName("无学生时下钻返回空列表")
    void testDrillEmpty() {
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.drillDown(null, "COLLEGE", null).isEmpty());
    }

    // ==================== 风险事件时间线 ====================

    @Test
    @DisplayName("时间线汇聚预警、干预、成绩与考勤事件")
    void testTimeline() {
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(warning(1L, 3, "ACTIVE")));
        InterventionTicket t = new InterventionTicket();
        t.setId(10L);
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(t));
        InterventionLog log = new InterventionLog();
        log.setTicketId(10L);
        log.setActionType("ACTION");
        log.setActionContent("已约谈");
        log.setCreateTime(LocalDateTime.now());
        Mockito.when(logMapper.selectList(any())).thenReturn(List.of(log));

        Score sc = new Score();
        sc.setId(20L);
        sc.setStudentId(1L);
        sc.setCourseId(1L);
        sc.setTotalScore(new BigDecimal("45"));
        sc.setIsPass(0);
        sc.setUpdateTime(LocalDateTime.now());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        Attendance a = new Attendance();
        a.setId(30L);
        a.setStatus("ABSENT");
        a.setCheckDate(LocalDate.now());
        Mockito.when(attendanceMapper.selectList(any())).thenReturn(List.of(a));

        List<WarningAnalyticsVO.TimelineItem> items = service.timeline(1L);
        assertEquals(4, items.size());
        assertTrue(items.stream().anyMatch(i -> "WARNING".equals(i.getEventType())));
        assertTrue(items.stream().anyMatch(i -> "INTERVENTION".equals(i.getEventType())));
        assertTrue(items.stream().anyMatch(i -> "SCORE".equals(i.getEventType())));
        assertTrue(items.stream().anyMatch(i -> "ATTENDANCE".equals(i.getEventType())));
    }

    @Test
    @DisplayName("时间线按时间倒序，最新在最前")
    void testTimelineOrder() {
        WarningRecord older = warning(1L, 2, "ACTIVE");
        older.setCreateTime(LocalDateTime.now().minusDays(10));
        WarningRecord newer = warning(1L, 4, "ACTIVE");
        newer.setCreateTime(LocalDateTime.now());
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(older, newer));
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(attendanceMapper.selectList(any())).thenReturn(List.of());

        List<WarningAnalyticsVO.TimelineItem> items = service.timeline(1L);
        assertEquals(2, items.size());
        assertEquals("红色预警", items.get(0).getTitle());
        assertEquals("黄色预警", items.get(1).getTitle());
    }

    @Test
    @DisplayName("及格成绩不进入时间线")
    void testTimelineOnlyFail() {
        Score sc = new Score();
        sc.setTotalScore(new BigDecimal("85"));
        sc.setIsPass(1);
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(attendanceMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.timeline(1L).isEmpty());
    }

    @Test
    @DisplayName("干预动作类型转为中文便于阅读")
    void testTimelineActionText() {
        InterventionTicket t = new InterventionTicket();
        t.setId(10L);
        Mockito.when(ticketMapper.selectList(any())).thenReturn(List.of(t));
        InterventionLog log = new InterventionLog();
        log.setActionType("CLOSE");
        log.setCreateTime(LocalDateTime.now());
        Mockito.when(logMapper.selectList(any())).thenReturn(List.of(log));
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(attendanceMapper.selectList(any())).thenReturn(List.of());

        assertEquals("关闭工单", service.timeline(1L).get(0).getTitle());
    }

    // ==================== 学业触发判定 ====================

    @Test
    @DisplayName("欠学分达 8 触发橙色预警")
    void testTriggerHighDeficit() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("9.00"));
        vo.setTrendSlope(BigDecimal.ZERO);
        vo.setDeviation(BigDecimal.ZERO);
        vo.setReason("累计欠学分 9.00");
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(vo);

        WarningAnalyticsVO.AcademicTrigger trigger = service.evalAcademicTrigger(1L, null);
        assertTrue(trigger.getTriggered());
        assertEquals(3, trigger.getSuggestLevel());
    }

    @Test
    @DisplayName("成绩大幅下滑触发橙色预警")
    void testTriggerSlope() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setTrendSlope(new BigDecimal("-10.00"));
        vo.setDeviation(BigDecimal.ZERO);
        vo.setReason("成绩下滑");
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(vo);

        assertEquals(3, service.evalAcademicTrigger(1L, null).getSuggestLevel());
    }

    @Test
    @DisplayName("轻度异常触发黄色预警")
    void testTriggerMild() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("5.00"));
        vo.setTrendSlope(new BigDecimal("-2.00"));
        vo.setDeviation(BigDecimal.ZERO);
        vo.setReason("欠学分 5");
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(vo);

        WarningAnalyticsVO.AcademicTrigger trigger = service.evalAcademicTrigger(1L, null);
        assertTrue(trigger.getTriggered());
        assertEquals(2, trigger.getSuggestLevel());
    }

    @Test
    @DisplayName("指标正常时不触发")
    void testTriggerNormal() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setTrendSlope(new BigDecimal("1.00"));
        vo.setDeviation(new BigDecimal("0.50"));
        vo.setReason("各项学业指标正常");
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(vo);

        WarningAnalyticsVO.AcademicTrigger trigger = service.evalAcademicTrigger(1L, null);
        assertFalse(trigger.getTriggered());
        assertEquals(0, trigger.getSuggestLevel());
    }

    @Test
    @DisplayName("触发结果携带指标明细供核查")
    void testTriggerCarriesMetrics() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("6.00"));
        vo.setTrendSlope(new BigDecimal("-5.00"));
        vo.setDeviation(new BigDecimal("-1.20"));
        vo.setReason("依据");
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(vo);

        WarningAnalyticsVO.AcademicTrigger trigger = service.evalAcademicTrigger(1L, null);
        assertEquals(0, new BigDecimal("6.00").compareTo(trigger.getDeficitCredit()));
        assertEquals(0, new BigDecimal("-5.00").compareTo(trigger.getTrendSlope()));
        assertEquals(0, new BigDecimal("-1.20").compareTo(trigger.getDeviation()));
        assertEquals("依据", trigger.getReason());
    }
}
