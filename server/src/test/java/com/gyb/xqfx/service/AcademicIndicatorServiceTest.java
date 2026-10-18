package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 学业核心指标服务测试
 */
@DisplayName("学业核心指标服务测试")
class AcademicIndicatorServiceTest {

    private ScoreMapper scoreMapper;
    private CourseMapper courseMapper;
    private StudentMapper studentMapper;
    private TermMapper termMapper;
    private DataScopeService dataScopeService;
    private AcademicIndicatorService service;

    @BeforeEach
    void setUp() {
        scoreMapper = Mockito.mock(ScoreMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        termMapper = Mockito.mock(TermMapper.class);
        dataScopeService = Mockito.mock(DataScopeService.class);
        service = new AcademicIndicatorService(scoreMapper, courseMapper, studentMapper, termMapper,
                dataScopeService);
    }

    private Score score(Long termId, Long courseId, String total, Integer pass) {
        Score s = new Score();
        s.setStudentId(1L);
        s.setTermId(termId);
        s.setCourseId(courseId);
        s.setTotalScore(new BigDecimal(total));
        s.setIsPass(pass);
        return s;
    }

    private Course course(Long id, String credit) {
        Course c = new Course();
        c.setId(id);
        c.setCredit(new BigDecimal(credit));
        return c;
    }

    // ==================== 绩点换算 ====================

    @ParameterizedTest
    @CsvSource({"100, 4.0", "90, 4.0", "89, 3.7", "85, 3.7", "84, 3.3", "82, 3.3",
            "81, 3.0", "78, 3.0", "77, 2.7", "75, 2.7", "74, 2.3", "72, 2.3",
            "71, 2.0", "68, 2.0", "67, 1.5", "64, 1.5", "63, 1.0", "60, 1.0",
            "59, 0.0", "0, 0.0"})
    @DisplayName("百分制成绩按标准换算为绩点")
    void testGradePoint(String score, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(service.gradePoint(new BigDecimal(score))));
    }

    @Test
    @DisplayName("成绩为空时绩点为 0")
    void testGradePointNull() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.gradePoint(null)));
    }

    // ==================== 回归斜率 ====================

    @Test
    @DisplayName("成绩持续下降时斜率为负")
    void testSlopeDown() {
        BigDecimal slope = service.linearSlope(List.of(
                new BigDecimal("85"), new BigDecimal("75"), new BigDecimal("62")));
        assertTrue(slope.compareTo(BigDecimal.ZERO) < 0, "斜率应为负，实际：" + slope);
        assertEquals(0, new BigDecimal("-11.50").compareTo(slope));
    }

    @Test
    @DisplayName("成绩持续上升时斜率为正")
    void testSlopeUp() {
        assertTrue(service.linearSlope(List.of(
                new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("80")))
                .compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("成绩持平时斜率为 0")
    void testSlopeFlat() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.linearSlope(List.of(
                new BigDecimal("70"), new BigDecimal("70"), new BigDecimal("70")))));
    }

    @Test
    @DisplayName("样本不足两点时斜率为 0")
    void testSlopeInsufficient() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.linearSlope(List.of(new BigDecimal("70")))));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.linearSlope(List.of())));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.linearSlope(null)));
    }

    // ==================== GPA 与学分 ====================

    @Test
    @DisplayName("按学分加权计算 GPA 并统计欠学分")
    void testCalculate() {
        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("2021001");
        s.setName("张三");
        Mockito.when(studentMapper.selectById(1L)).thenReturn(s);
        Mockito.when(courseMapper.selectById(1L)).thenReturn(course(1L, "4.0"));
        Mockito.when(courseMapper.selectById(2L)).thenReturn(course(2L, "3.0"));
        // 90 分 4 学分（绩点4.0，及格）+ 50 分 3 学分（绩点0，不及格）
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(2L, 1L, "90", 1), score(2L, 2L, "50", 0)));

        AcademicIndicatorVO vo = service.calculate(1L, 2L);
        assertEquals("张三", vo.getStudentName());
        assertEquals(2, vo.getCourseCount());
        assertEquals(0, new BigDecimal("7.00").compareTo(vo.getTotalCredit()));
        assertEquals(0, new BigDecimal("4.00").compareTo(vo.getEarnedCredit()));
        assertEquals(0, new BigDecimal("3.00").compareTo(vo.getDeficitCredit()));
        assertEquals(1, vo.getFailCount());
        // GPA = (4.0*4 + 0*3) / 7 = 16/7 ≈ 2.29
        assertEquals(0, new BigDecimal("2.29").compareTo(vo.getGpa()));
    }

    @Test
    @DisplayName("课程未配置学分时按默认学分计算")
    void testDefaultCredit() {
        Mockito.when(courseMapper.selectById(any(Long.class))).thenReturn(null);
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(score(2L, 9L, "80", 1)));

        AcademicIndicatorVO vo = service.calculate(1L, 2L);
        assertEquals(0, new BigDecimal("3.00").compareTo(vo.getTotalCredit()));
        assertEquals(0, new BigDecimal("3.00").compareTo(vo.getEarnedCredit()));
    }

    @Test
    @DisplayName("无成绩数据时指标归零且判定正常")
    void testNoScore() {
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        AcademicIndicatorVO vo = service.calculate(1L, null);
        assertEquals(0, vo.getCourseCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getGpa()));
        assertEquals("NORMAL", vo.getRiskFlag());
    }

    // ==================== 风险判定 ====================

    @Test
    @DisplayName("累计欠学分达到警戒线判为风险")
    void testDeficitRisk() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("8"));
        vo.setTrendSlope(BigDecimal.ZERO);
        vo.setDeviation(BigDecimal.ZERO);
        vo.setGpa(new BigDecimal("2.0"));
        assertEquals("RISK", service.evalRiskFlag(vo));
    }

    @Test
    @DisplayName("成绩大幅下滑判为风险")
    void testSlopeRisk() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setTrendSlope(new BigDecimal("-9.00"));
        vo.setDeviation(BigDecimal.ZERO);
        vo.setGpa(new BigDecimal("2.5"));
        assertEquals("RISK", service.evalRiskFlag(vo));
    }

    @Test
    @DisplayName("欠学分达到关注线但未达风险线判为关注")
    void testAttention() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("5"));
        vo.setTrendSlope(BigDecimal.ZERO);
        vo.setDeviation(BigDecimal.ZERO);
        vo.setGpa(new BigDecimal("2.5"));
        assertEquals("ATTENTION", service.evalRiskFlag(vo));
    }

    @Test
    @DisplayName("各项指标正常时判为正常")
    void testNormal() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setTrendSlope(new BigDecimal("1.00"));
        vo.setDeviation(new BigDecimal("0.50"));
        vo.setGpa(new BigDecimal("3.2"));
        assertEquals("NORMAL", service.evalRiskFlag(vo));
    }

    @Test
    @DisplayName("判定依据可解释，供辅导员直接使用")
    void testReason() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(new BigDecimal("6.00"));
        vo.setFailCount(2);
        vo.setTrendSlope(new BigDecimal("-5.50"));
        vo.setDeviation(BigDecimal.ZERO);
        String reason = service.buildReason(vo);
        assertTrue(reason.contains("欠学分"), reason);
        assertTrue(reason.contains("下滑"), reason);
    }

    @Test
    @DisplayName("全部正常时依据文案明确说明")
    void testReasonNormal() {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setDeficitCredit(BigDecimal.ZERO);
        vo.setTrendSlope(BigDecimal.ZERO);
        vo.setDeviation(BigDecimal.ZERO);
        assertTrue(service.buildReason(vo).contains("正常"));
    }

    // ==================== 批量与序列 ====================

    @Test
    @DisplayName("批量计算按风险优先排序")
    void testBatchSorted() {
        Student s1 = new Student();
        s1.setId(1L);
        Student s2 = new Student();
        s2.setId(2L);
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2));
        Mockito.when(courseMapper.selectById(any(Long.class))).thenReturn(course(1L, "3.0"));
        // 学生1：不及格 2 门（欠 6 学分）；学生2：全部及格
        Mockito.when(scoreMapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<Score> w = inv.getArgument(0);
            return List.of();
        });

        List<AcademicIndicatorVO> list = service.batchCalculate(null, 0);
        assertEquals(2, list.size());
        list.forEach(v -> assertEquals("NORMAL", v.getRiskFlag()));
    }

    @Test
    @DisplayName("学期均分序列按学期升序")
    void testTermAvgSeries() {
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(2L, 1L, "80", 1), score(1L, 1L, "60", 1), score(1L, 2L, "70", 1)));
        List<BigDecimal> series = service.termAvgSeries(1L);
        assertEquals(2, series.size());
        // 学期1均分 65，学期2均分 80
        assertEquals(0, new BigDecimal("65.00").compareTo(series.get(0)));
        assertEquals(0, new BigDecimal("80.00").compareTo(series.get(1)));
    }
}
