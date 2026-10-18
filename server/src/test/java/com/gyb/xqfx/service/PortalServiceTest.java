package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
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
 * 学生端门户测试：学生只能访问本人数据
 */
@DisplayName("学生端门户测试")
class PortalServiceTest {

    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private CourseMapper courseMapper;
    private WarningRecordMapper warningRecordMapper;
    private AcademicIndicatorService indicatorService;
    private DataScopeService dataScopeService;
    private PortalService service;

    @BeforeEach
    void setUp() {
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        indicatorService = Mockito.mock(AcademicIndicatorService.class);
        dataScopeService = Mockito.mock(DataScopeService.class);
        service = new PortalService(studentMapper, scoreMapper, courseMapper, warningRecordMapper,
                indicatorService, dataScopeService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsStudent(Long userId, Long studentId) {
        LoginUser user = new LoginUser(userId, "s1", "STUDENT",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))).setStudentId(studentId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    @DisplayName("学生身份解析为本人学籍ID")
    void testMyStudentId() {
        loginAsStudent(5L, 30L);
        assertEquals(30L, service.myStudentId());
    }

    @Test
    @DisplayName("未绑定学籍的账号无法使用学生端")
    void testNoStudentBinding() {
        LoginUser user = new LoginUser(9L, "x", "STUDENT",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        Mockito.when(dataScopeService.currentStudentId()).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.myStudentId());
    }

    @Test
    @DisplayName("我的学情总览只包含本人数据与面向学生的建议")
    void testOverview() {
        loginAsStudent(5L, 30L);
        Student s = new Student();
        s.setId(30L);
        s.setStudentNo("2021001");
        s.setName("张三");
        Mockito.when(studentMapper.selectById(30L)).thenReturn(s);

        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setGpa(new BigDecimal("2.80"));
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setRiskFlag("NORMAL");
        Mockito.when(indicatorService.calculate(30L, null)).thenReturn(vo);

        WarningRecord w = new WarningRecord();
        w.setId(1L);
        w.setWarningLevel(2);
        w.setStatus("ACTIVE");
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(w));

        Score sc = new Score();
        sc.setId(11L);
        sc.setStudentId(30L);
        sc.setCourseId(1L);
        sc.setTotalScore(new BigDecimal("85"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));
        Mockito.when(courseMapper.selectById(1L)).thenReturn(null);

        PortalService.MyOverview overview = service.overview(null);
        assertEquals("张三", overview.getStudentName());
        assertEquals(1, overview.getWarningCount());
        assertEquals(1, overview.getActiveWarningCount());
        assertEquals(1, overview.getScoreCount());
        assertEquals(1, overview.getRecentScores().size());
        // 学生端预警只给建议行动，不含内部处置字段
        PortalService.WarningItem item = overview.getWarnings().get(0);
        assertNotNull(item.getAdvice());
        assertTrue(item.getAdvice().contains("辅导员"));
    }

    @Test
    @DisplayName("我的成绩仅返回本人成绩")
    void testMyScores() {
        loginAsStudent(5L, 30L);
        Score sc = new Score();
        sc.setStudentId(30L);
        sc.setCourseId(1L);
        sc.setTotalScore(new BigDecimal("72"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));
        Mockito.when(courseMapper.selectById(1L)).thenReturn(null);

        List<PortalService.ScoreItem> list = service.myScores(null);
        assertEquals(1, list.size());
        assertEquals(0, new BigDecimal("72").compareTo(list.get(0).getTotalScore()));
    }

    @Test
    @DisplayName("我的学业指标取自本人")
    void testMyIndicator() {
        loginAsStudent(5L, 30L);
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setGpa(new BigDecimal("3.10"));
        Mockito.when(indicatorService.calculate(30L, 2L)).thenReturn(vo);

        assertEquals(0, new BigDecimal("3.10").compareTo(service.myIndicator(2L).getGpa()));
    }

    @Test
    @DisplayName("不同等级给出不同的学生建议")
    void testAdviceByLevel() {
        loginAsStudent(5L, 30L);
        Mockito.when(studentMapper.selectById(30L)).thenReturn(new Student());
        Mockito.when(indicatorService.calculate(any(), any())).thenReturn(new AcademicIndicatorVO());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());

        WarningRecord red = new WarningRecord();
        red.setWarningLevel(4);
        red.setStatus("ACTIVE");
        WarningRecord blue = new WarningRecord();
        blue.setWarningLevel(1);
        blue.setStatus("ACTIVE");
        Mockito.when(warningRecordMapper.selectList(any())).thenReturn(List.of(red, blue));

        var warnings = service.overview(null).getWarnings();
        String redAdvice = warnings.stream().filter(w -> w.getWarningLevel() == 4)
                .findFirst().orElseThrow().getAdvice();
        String blueAdvice = warnings.stream().filter(w -> w.getWarningLevel() == 1)
                .findFirst().orElseThrow().getAdvice();
        assertTrue(redAdvice.contains("立即"));
        assertNotEquals(redAdvice, blueAdvice);
    }
}
