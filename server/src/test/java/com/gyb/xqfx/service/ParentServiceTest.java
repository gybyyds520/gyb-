package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Attendance;
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
import com.gyb.xqfx.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 家长端测试：只能查看已绑定子女，且不含内部流程信息
 */
@DisplayName("家长端测试")
class ParentServiceTest {

    private StudentParentMapper studentParentMapper;
    private StudentMapper studentMapper;
    private ClazzMapper clazzMapper;
    private ScoreMapper scoreMapper;
    private AttendanceMapper attendanceMapper;
    private WarningRecordMapper warningRecordMapper;
    private ParentFeedbackMapper feedbackMapper;
    private AcademicIndicatorService indicatorService;
    private ParentService service;

    @BeforeEach
    void setUp() {
        studentParentMapper = Mockito.mock(StudentParentMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        attendanceMapper = Mockito.mock(AttendanceMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        feedbackMapper = Mockito.mock(ParentFeedbackMapper.class);
        indicatorService = Mockito.mock(AcademicIndicatorService.class);
        service = new ParentService(studentParentMapper, studentMapper, clazzMapper, scoreMapper,
                attendanceMapper, warningRecordMapper, feedbackMapper, indicatorService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsParent(Long userId) {
        LoginUser user = new LoginUser(userId, "p1", "PARENT",
                List.of(new SimpleGrantedAuthority("ROLE_PARENT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private StudentParent bind(Long studentId, Long parentUserId) {
        StudentParent sp = new StudentParent();
        sp.setStudentId(studentId);
        sp.setParentUserId(parentUserId);
        sp.setStatus(1);
        return sp;
    }

    @Test
    @DisplayName("家长只能看到已绑定的子女")
    void testMyChildIds() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));
        assertEquals(List.of(40L), service.myChildIds());
    }

    @Test
    @DisplayName("查看未绑定学生时被拒绝")
    void testRequireChildDenied() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));

        assertDoesNotThrow(() -> service.requireChild(40L));
        assertThrows(BusinessException.class, () -> service.requireChild(999L),
                "家长不能查看非监护学生");
        assertThrows(BusinessException.class, () -> service.requireChild(null));
    }

    @Test
    @DisplayName("对非监护学生提交回执时被拒绝")
    void testFeedbackDenied() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));
        assertThrows(BusinessException.class,
                () -> service.submitFeedback(999L, "WARNING", null, "ACKNOWLEDGED", ""));
    }

    @Test
    @DisplayName("子女列表包含学业概况与最高预警等级")
    void testMyChildren() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));
        Student s = new Student();
        s.setId(40L);
        s.setStudentNo("2021001");
        s.setName("李四");
        s.setClazzId(1L);
        Mockito.when(studentMapper.selectById(40L)).thenReturn(s);
        Mockito.when(clazzMapper.selectById(1L)).thenReturn(null);

        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setGpa(new BigDecimal("2.10"));
        vo.setDeficitCredit(new BigDecimal("6.00"));
        vo.setTrendSlope(new BigDecimal("-5.00"));
        vo.setRiskFlag("ATTENTION");
        Mockito.when(indicatorService.calculate(40L, null)).thenReturn(vo);

        WarningRecord w = new WarningRecord();
        w.setWarningLevel(3);
        w.setStatus("ACTIVE");
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(w));

        List<ParentService.ChildBrief> children = service.myChildren(null);
        assertEquals(1, children.size());
        ParentService.ChildBrief brief = children.get(0);
        assertEquals("李四", brief.getStudentName());
        assertEquals(0, new BigDecimal("2.10").compareTo(brief.getGpa()));
        assertEquals(0, new BigDecimal("6.00").compareTo(brief.getDeficitCredit()));
        assertEquals(1, brief.getActiveWarningCount());
        assertEquals(3, brief.getTopWarningLevel());
    }

    @Test
    @DisplayName("子女详情包含成绩、出勤与面向家长的建议")
    void testChildDetail() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));
        Student s = new Student();
        s.setId(40L);
        s.setName("李四");
        Mockito.when(studentMapper.selectById(40L)).thenReturn(s);
        Mockito.when(clazzMapper.selectById(any())).thenReturn(null);
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(new AcademicIndicatorVO());

        Score pass = new Score();
        pass.setTotalScore(new BigDecimal("80"));
        pass.setIsPass(1);
        Score fail = new Score();
        fail.setTotalScore(new BigDecimal("45"));
        fail.setIsPass(0);
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(pass, fail));

        Attendance present = new Attendance();
        present.setStatus("PRESENT");
        Attendance absent = new Attendance();
        absent.setStatus("ABSENT");
        Mockito.when(attendanceMapper.selectList(any())).thenReturn(List.of(present, absent));

        WarningRecord w = new WarningRecord();
        w.setWarningLevel(4);
        w.setStatus("ACTIVE");
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(w));
        Mockito.when(feedbackMapper.selectList(any())).thenReturn(List.of());

        ParentService.ChildDetail detail = service.childDetail(40L, null);
        assertEquals(2, detail.getScoreCount());
        assertEquals(1, detail.getFailCount());
        assertEquals(0, new BigDecimal("62.50").compareTo(detail.getAvgScore()));
        assertEquals(2, detail.getAttendanceCount());
        assertEquals(1, detail.getAbsentCount());
        assertEquals(1, detail.getWarnings().size());
        assertTrue(detail.getWarnings().get(0).getAdvice().contains("辅导员"));
    }

    @Test
    @DisplayName("家长可提交回执")
    void testSubmitFeedback() {
        loginAsParent(6L);
        Mockito.when(studentParentMapper.selectList(any())).thenReturn(List.of(bind(40L, 6L)));
        Mockito.doAnswer(inv -> {
            com.gyb.xqfx.entity.ParentFeedback fb = inv.getArgument(0);
            fb.setId(100L);
            return 1;
        }).when(feedbackMapper).insert(any(com.gyb.xqfx.entity.ParentFeedback.class));

        Long id = service.submitFeedback(40L, "WARNING", 7L, "SUPPORTED", "已与孩子沟通");
        assertEquals(100L, id);
    }
}
