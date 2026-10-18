package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.ProfileSnapshot;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ProfileSnapshotMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentTagMapper;
import com.gyb.xqfx.mapper.TagDefinitionMapper;
import com.gyb.xqfx.model.vo.BiasVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 学生画像服务测试（维度计算与标签规则匹配为纯逻辑）
 */
@DisplayName("学生画像服务测试")
class ProfileServiceTest {

    private ProfileService service;
    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private ProfileSnapshotMapper snapshotMapper;
    private CourseMapper courseMapper;

    @BeforeEach
    void setUp() {
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        snapshotMapper = Mockito.mock(ProfileSnapshotMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        service = new ProfileService(
                studentMapper,
                scoreMapper,
                Mockito.mock(AttendanceMapper.class),
                Mockito.mock(OnlineBehaviorMapper.class),
                Mockito.mock(PredictionResultMapper.class),
                snapshotMapper,
                Mockito.mock(StudentTagMapper.class),
                Mockito.mock(TagDefinitionMapper.class),
                Mockito.mock(ClazzMapper.class),
                courseMapper,
                new ObjectMapper());
    }

    private Map<String, Double> metrics(double avg, double fail, double attendance,
                                        double minutes, double video, double submit, double risk) {
        Map<String, Double> m = new HashMap<>();
        m.put("avg_score", avg);
        m.put("fail_count", fail);
        m.put("attendance_rate", attendance);
        m.put("study_minutes", minutes);
        m.put("video_progress", video);
        m.put("assignment_submit_rate", submit);
        m.put("risk_probability", risk);
        return m;
    }

    // ==================== 五维计算 ====================

    @Test
    @DisplayName("五维分数均为 0-100")
    void testDimensionsInRange() {
        Map<String, BigDecimal> dims = service.computeDimensions(metrics(75, 0, 0.9, 500, 80, 0.9, 0.3));
        assertEquals(5, dims.size());
        dims.forEach((k, v) -> {
            assertTrue(v.compareTo(BigDecimal.ZERO) >= 0, k + " 应 >= 0");
            assertTrue(v.compareTo(new BigDecimal("100")) <= 0, k + " 应 <= 100");
        });
    }

    @Test
    @DisplayName("成绩越高学业维度分越高")
    void testAcademicMonotonic() {
        BigDecimal low = service.computeDimensions(metrics(50, 0, 1.0, 500, 80, 1.0, 0.0)).get("academic");
        BigDecimal high = service.computeDimensions(metrics(90, 0, 1.0, 500, 80, 1.0, 0.0)).get("academic");
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    @DisplayName("挂科会拉低学业维度分")
    void testFailCountPenalty() {
        BigDecimal noFail = service.computeDimensions(metrics(80, 0, 1.0, 500, 80, 1.0, 0.0)).get("academic");
        BigDecimal withFail = service.computeDimensions(metrics(80, 3, 1.0, 500, 80, 1.0, 0.0)).get("academic");
        assertTrue(withFail.compareTo(noFail) < 0);
    }

    @Test
    @DisplayName("出勤率决定投入与行为维度")
    void testAttendanceImpact() {
        var high = service.computeDimensions(metrics(80, 0, 1.0, 500, 80, 1.0, 0.0));
        var low = service.computeDimensions(metrics(80, 0, 0.5, 500, 80, 1.0, 0.0));
        assertTrue(high.get("engagement").compareTo(low.get("engagement")) > 0);
        assertTrue(high.get("behavior").compareTo(low.get("behavior")) > 0);
    }

    @ParameterizedTest
    @CsvSource({"0.0, 0", "0.5, 50", "1.0, 100"})
    @DisplayName("风险概率直接映射风险指数")
    void testRiskMapping(double probability, String expected) {
        BigDecimal risk = service.computeDimensions(metrics(80, 0, 1.0, 500, 80, 1.0, probability)).get("risk");
        assertEquals(0, risk.compareTo(new BigDecimal(expected)));
    }

    @Test
    @DisplayName("极端输入也被约束在 0-100")
    void testClamp() {
        var dims = service.computeDimensions(metrics(200, 20, 2.0, 99999, 200, 5.0, 9.0));
        dims.forEach((k, v) -> assertTrue(v.compareTo(new BigDecimal("100")) <= 0, k + " 超上限"));
        var negatives = service.computeDimensions(metrics(-100, -5, -1.0, -50, -20, -1.0, -1.0));
        negatives.forEach((k, v) -> assertTrue(v.compareTo(BigDecimal.ZERO) >= 0, k + " 低于下限"));
    }

    @Test
    @DisplayName("空指标不抛异常")
    void testEmptyMetrics() {
        assertDoesNotThrow(() -> service.computeDimensions(new HashMap<>()));
    }

    // ==================== 标签规则 ====================

    @Test
    @DisplayName("单条件 gte 命中")
    void testRuleGte() {
        Map<String, Double> m = metrics(80, 1, 1.0, 0, 0, 0, 0);
        assertTrue(service.matchRule("{\"fail_count\":{\"gte\":1}}", m));
        assertFalse(service.matchRule("{\"fail_count\":{\"gte\":2}}", m));
    }

    @ParameterizedTest
    @CsvSource({"lt, 59, true", "lt, 60, false", "lte, 60, true", "gt, 61, true", "gt, 60, false", "gte, 60, true", "eq, 60, true"})
    @DisplayName("五种操作符边界正确")
    void testOperators(String op, String value, boolean expected) {
        Map<String, Double> m = new HashMap<>();
        m.put("avg_score", Double.parseDouble(value));
        assertEquals(expected, service.matchRule("{\"avg_score\":{\"" + op + "\":60}}", m));
    }

    @Test
    @DisplayName("多条件需全部满足")
    void testMultiCondition() {
        Map<String, Double> m = metrics(85, 0, 0.95, 0, 0, 0, 0);
        assertTrue(service.matchRule("{\"avg_score\":{\"gte\":85},\"attendance_rate\":{\"gte\":0.9}}", m));

        Map<String, Double> m2 = metrics(85, 0, 0.50, 0, 0, 0, 0);
        assertFalse(service.matchRule("{\"avg_score\":{\"gte\":85},\"attendance_rate\":{\"gte\":0.9}}", m2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-json", "{}"})
    @DisplayName("空或非法规则不命中")
    void testInvalidRule(String rule) {
        Map<String, Double> m = metrics(80, 1, 1.0, 0, 0, 0, 0);
        assertFalse(service.matchRule(rule, m));
    }

    @Test
    @DisplayName("规则为 null 不命中")
    void testNullRule() {
        assertFalse(service.matchRule(null, metrics(80, 1, 1.0, 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("未知字段按 0 处理")
    void testUnknownField() {
        assertTrue(service.matchRule("{\"unknown_field\":{\"lt\":10}}", new HashMap<>()));
    }

    // ==================== 偏科分析 ====================

    @Test
    @DisplayName("偏科分析：强科/弱科判定、百分位与偏科指数")
    void testBiasAnalysis() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");
        student.setClazzId(10L);
        when(studentMapper.selectById(1L)).thenReturn(student);

        // 本人成绩：高数 90、大学英语 55
        Score s1 = new Score();
        s1.setStudentId(1L);
        s1.setCourseId(100L);
        s1.setTotalScore(new BigDecimal("90"));
        Score s2 = new Score();
        s2.setStudentId(1L);
        s2.setCourseId(200L);
        s2.setTotalScore(new BigDecimal("55"));

        // 同班同学：高数 70/80（基准 75），英语 70/75（基准 72.5）
        Score ps1 = new Score();
        ps1.setStudentId(2L);
        ps1.setCourseId(100L);
        ps1.setTotalScore(new BigDecimal("70"));
        Score ps2 = new Score();
        ps2.setStudentId(3L);
        ps2.setCourseId(100L);
        ps2.setTotalScore(new BigDecimal("80"));
        Score ps3 = new Score();
        ps3.setStudentId(2L);
        ps3.setCourseId(200L);
        ps3.setTotalScore(new BigDecimal("70"));
        Score ps4 = new Score();
        ps4.setStudentId(3L);
        ps4.setCourseId(200L);
        ps4.setTotalScore(new BigDecimal("75"));

        when(scoreMapper.selectList(any())).thenReturn(List.of(s1, s2), List.of(ps1, ps2, ps3, ps4));

        Student p2 = new Student();
        p2.setId(2L);
        p2.setClazzId(10L);
        Student p3 = new Student();
        p3.setId(3L);
        p3.setClazzId(10L);
        when(studentMapper.selectList(any())).thenReturn(List.of(p2, p3));

        Course c1 = new Course();
        c1.setId(100L);
        c1.setCourseName("高等数学");
        c1.setCourseType("MAJOR");
        Course c2 = new Course();
        c2.setId(200L);
        c2.setCourseName("大学英语");
        c2.setCourseType("CORE");
        when(courseMapper.selectById(100L)).thenReturn(c1);
        when(courseMapper.selectById(200L)).thenReturn(c2);

        BiasVO vo = service.biasAnalysis(1L, null);
        assertEquals(2, vo.getTotal());
        assertEquals(1, vo.getStrongCount());
        assertEquals(1, vo.getWeakCount());
        assertEquals("CLAZZ", vo.getBaseline());

        Map<Long, BiasVO.BiasItem> byCourse = new HashMap<>();
        vo.getItems().forEach(i -> byCourse.put(i.getCourseId(), i));

        BiasVO.BiasItem strong = byCourse.get(100L);
        assertEquals("STRONG", strong.getVerdict());
        assertEquals(100, strong.getRankPercent());
        assertEquals(0, new BigDecimal("15.00").compareTo(strong.getDiff()));

        BiasVO.BiasItem weak = byCourse.get(200L);
        assertEquals("WEAK", weak.getVerdict());
        assertEquals(0, weak.getRankPercent());
        assertEquals(0, new BigDecimal("-17.50").compareTo(weak.getDiff()));

        // 偏科指数 = 弱科占比(0.5) × 平均弱科差距(17.5) = 8.75
        assertEquals(0, new BigDecimal("8.75").compareTo(vo.getBiasIndex()));
    }

    @Test
    @DisplayName("偏科分析：无课程成绩时返回空结果不抛异常")
    void testBiasAnalysisEmpty() {
        Student student = new Student();
        student.setId(1L);
        student.setName("李四");
        student.setClazzId(10L);
        when(studentMapper.selectById(1L)).thenReturn(student);
        when(scoreMapper.selectList(any())).thenReturn(List.of());

        BiasVO vo = service.biasAnalysis(1L, null);
        assertEquals(0, vo.getTotal());
        assertEquals(0, vo.getWeakCount());
        assertEquals(0, new BigDecimal("0").compareTo(vo.getBiasIndex()));
    }

    // ==================== 快照历史 ====================

    @Test
    @DisplayName("快照历史返回学生快照列表")
    void testSnapshots() {
        ProfileSnapshot s1 = new ProfileSnapshot();
        s1.setId(1L);
        s1.setStudentId(1L);
        ProfileSnapshot s2 = new ProfileSnapshot();
        s2.setId(2L);
        s2.setStudentId(1L);
        when(snapshotMapper.selectList(any())).thenReturn(List.of(s1, s2));

        List<ProfileSnapshot> list = service.snapshots(1L);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getStudentId());
    }
}
