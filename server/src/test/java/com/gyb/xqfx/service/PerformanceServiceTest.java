package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.ClassPerformance;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClassPerformanceMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.vo.PerformanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 课堂表现服务测试
 */
@DisplayName("课堂表现服务测试")
class PerformanceServiceTest {

    private ClassPerformanceMapper performanceMapper;
    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private PerformanceService service;

    @BeforeEach
    void setUp() {
        performanceMapper = Mockito.mock(ClassPerformanceMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        service = new PerformanceService(performanceMapper, studentMapper, scoreMapper);
    }

    private ClassPerformance record(Long studentId, String participation, String homework, int questions) {
        ClassPerformance r = new ClassPerformance();
        r.setStudentId(studentId);
        r.setParticipationScore(new BigDecimal(participation));
        r.setHomeworkScore(new BigDecimal(homework));
        r.setQuestionCount(questions);
        return r;
    }

    // ==================== 评级 ====================

    @ParameterizedTest
    @CsvSource({"10, 积极", "8, 积极", "7.99, 一般", "5, 一般", "4.99, 待提升", "0, 待提升"})
    @DisplayName("参与度评级边界正确")
    void testEvalLevel(String value, String expected) {
        assertEquals(expected, service.evalLevel(new BigDecimal(value)));
    }

    @Test
    @DisplayName("null 参与度视为待提升")
    void testEvalLevelNull() {
        assertEquals("待提升", service.evalLevel(null));
    }

    // ==================== 汇总 ====================

    @Test
    @DisplayName("汇总计算平均值与互动总数")
    void testSummary() {
        Student student = new Student();
        student.setId(1L);
        student.setStudentNo("2021001");
        student.setName("张三");
        Mockito.when(studentMapper.selectById(1L)).thenReturn(student);
        Mockito.when(performanceMapper.selectList(any())).thenReturn(List.of(
                record(1L, "8", "90", 3),
                record(1L, "6", "80", 1)));

        PerformanceVO vo = service.summary(1L, null);
        assertEquals("张三", vo.getStudentName());
        assertEquals(2, vo.getRecordCount());
        assertEquals(0, new BigDecimal("7.00").compareTo(vo.getAvgParticipation()));
        assertEquals(0, new BigDecimal("85.00").compareTo(vo.getAvgHomework()));
        assertEquals(4, vo.getTotalQuestions());
        assertEquals("一般", vo.getLevel());
    }

    @Test
    @DisplayName("无记录时汇总全为 0 且评级待提升")
    void testSummaryEmpty() {
        Mockito.when(studentMapper.selectById(2L)).thenReturn(null);
        Mockito.when(performanceMapper.selectList(any())).thenReturn(List.of());

        PerformanceVO vo = service.summary(2L, null);
        assertEquals(0, vo.getRecordCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getAvgParticipation()));
        assertEquals("待提升", vo.getLevel());
    }

    @Test
    @DisplayName("忽略空值字段避免空指针")
    void testSummaryNullFields() {
        ClassPerformance r = new ClassPerformance();
        r.setStudentId(3L);
        Mockito.when(performanceMapper.selectList(any())).thenReturn(List.of(r));

        PerformanceVO vo = assertDoesNotThrow(() -> service.summary(3L, null));
        assertEquals(1, vo.getRecordCount());
        assertEquals(0, vo.getTotalQuestions());
    }

    // ==================== 排名 ====================

    @Test
    @DisplayName("排名按平均参与度降序")
    void testRankingOrder() {
        Mockito.when(performanceMapper.selectList(any())).thenReturn(List.of(
                record(1L, "5", "70", 1),
                record(2L, "9", "95", 5)));

        List<PerformanceVO> ranking = service.ranking(null, 0);
        assertEquals(2, ranking.size());
        assertEquals(2L, ranking.get(0).getStudentId());
        assertEquals("积极", ranking.get(0).getLevel());
        assertEquals("一般", ranking.get(1).getLevel());
    }

    @Test
    @DisplayName("排名支持条数限制")
    void testRankingLimit() {
        Mockito.when(performanceMapper.selectList(any())).thenReturn(List.of(
                record(1L, "9", "90", 1),
                record(2L, "8", "85", 1),
                record(3L, "7", "80", 1)));
        assertEquals(2, service.ranking(null, 2).size());
    }

    // ==================== 记录 ====================

    @Test
    @DisplayName("记录时补齐默认日期与计数")
    void testRecordDefaults() {
        ClassPerformance r = new ClassPerformance();
        r.setStudentId(1L);
        r.setParticipationScore(new BigDecimal("8"));
        r.setHomeworkScore(new BigDecimal("85"));

        service.record(r);
        assertNotNull(r.getRecordDate());
        assertEquals(0, r.getQuestionCount());
        assertNotNull(r.getRemark());
        Mockito.verify(performanceMapper).insert(r);
    }

    @Test
    @DisplayName("记录时保留传入的日期与备注")
    void testRecordKeepsValues() {
        ClassPerformance r = new ClassPerformance();
        r.setStudentId(1L);
        r.setRecordDate(LocalDate.of(2024, 3, 1));
        r.setRemark("课堂发言积极");
        r.setQuestionCount(2);

        service.record(r);
        assertEquals(LocalDate.of(2024, 3, 1), r.getRecordDate());
        assertEquals("课堂发言积极", r.getRemark());
        assertEquals(2, r.getQuestionCount());
    }

    // ==================== 演示数据 ====================

    @Test
    @DisplayName("生成演示数据条数为 人数×次数")
    void testGenerateDemo() {
        Score s1 = new Score();
        s1.setStudentId(1L);
        s1.setTotalScore(new BigDecimal("85"));
        Score s2 = new Score();
        s2.setStudentId(2L);
        s2.setTotalScore(new BigDecimal("55"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(s1, s2));

        assertEquals(6, service.generateDemo(2L, 3));
        Mockito.verify(performanceMapper, Mockito.times(6)).insert(any(ClassPerformance.class));
    }

    @Test
    @DisplayName("演示数据取值在合理区间")
    void testGenerateDemoBounded() {
        Score s = new Score();
        s.setStudentId(1L);
        s.setTotalScore(new BigDecimal("100"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(s));

        var captor = org.mockito.ArgumentCaptor.forClass(ClassPerformance.class);
        service.generateDemo(2L, 4);
        Mockito.verify(performanceMapper, Mockito.times(4)).insert(captor.capture());
        captor.getAllValues().forEach(r -> {
            assertTrue(r.getParticipationScore().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(r.getParticipationScore().compareTo(new BigDecimal("10")) <= 0);
            assertTrue(r.getHomeworkScore().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(r.getHomeworkScore().compareTo(new BigDecimal("100")) <= 0);
            assertNotNull(r.getRecordDate());
        });
    }

    @Test
    @DisplayName("无成绩数据时生成演示数据失败")
    void testGenerateDemoNoScore() {
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.generateDemo(2L, 3));
    }
}
