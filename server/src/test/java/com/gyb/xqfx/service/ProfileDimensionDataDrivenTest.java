package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 五维画像数据驱动测试：覆盖指标区间组合下的画像分不变式
 */
@DisplayName("画像维度数据驱动测试")
class ProfileDimensionDataDrivenTest {

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(
                Mockito.mock(StudentMapper.class),
                Mockito.mock(ScoreMapper.class),
                Mockito.mock(AttendanceMapper.class),
                Mockito.mock(OnlineBehaviorMapper.class),
                Mockito.mock(PredictionResultMapper.class),
                Mockito.mock(ProfileSnapshotMapper.class),
                Mockito.mock(StudentTagMapper.class),
                Mockito.mock(TagDefinitionMapper.class),
                Mockito.mock(ClazzMapper.class),
                Mockito.mock(CourseMapper.class),
                new ObjectMapper());
    }

    /** 生成指标组合：成绩 × 挂科 × 出勤 × 学习时长 × 风险概率 */
    static Stream<Arguments> metricCombos() {
        List<Arguments> args = new ArrayList<>();
        double[] avgScores = {0, 45, 60, 85, 100};
        int[] failCounts = {0, 2};
        double[] attendances = {0.5, 0.9, 1.0};
        int[] minutes = {0, 600};
        double[] risks = {0.0, 0.6, 1.0};
        for (double avg : avgScores) {
            for (int fail : failCounts) {
                for (double att : attendances) {
                    for (int min : minutes) {
                        for (double risk : risks) {
                            args.add(Arguments.of(avg, fail, att, min, risk));
                        }
                    }
                }
            }
        }
        return args.stream();
    }

    private Map<String, Double> metrics(double avg, int fail, double att, int minutes, double risk) {
        Map<String, Double> m = new HashMap<>();
        m.put("avg_score", avg);
        m.put("fail_count", (double) fail);
        m.put("attendance_rate", att);
        m.put("study_minutes", (double) minutes);
        m.put("video_progress", att * 100);
        m.put("assignment_submit_rate", att);
        m.put("risk_probability", risk);
        m.put("absent_count", (1 - att) * 20);
        return m;
    }

    @ParameterizedTest(name = "avg={0} fail={1} att={2} min={3} risk={4}")
    @MethodSource("metricCombos")
    @DisplayName("任意指标组合下五维均落在 0-100")
    void testDimensionsBounded(double avg, int fail, double att, int minutes, double risk) {
        Map<String, BigDecimal> dims = service.computeDimensions(metrics(avg, fail, att, minutes, risk));
        assertEquals(5, dims.size());
        dims.forEach((k, v) -> {
            assertNotNull(v, k + " 不应为 null");
            assertTrue(v.compareTo(BigDecimal.ZERO) >= 0, k + "=" + v + " 应 >= 0");
            assertTrue(v.compareTo(new BigDecimal("100")) <= 0, k + "=" + v + " 应 <= 100");
        });
    }

    @ParameterizedTest(name = "avg={0} fail={1} att={2} min={3} risk={4}")
    @MethodSource("metricCombos")
    @DisplayName("发展潜力介于学业与投入之间")
    void testPotentialBetween(double avg, int fail, double att, int minutes, double risk) {
        Map<String, BigDecimal> dims = service.computeDimensions(metrics(avg, fail, att, minutes, risk));
        double academic = dims.get("academic").doubleValue();
        double engagement = dims.get("engagement").doubleValue();
        double potential = dims.get("potential").doubleValue();
        double min = Math.min(academic, engagement);
        double max = Math.max(academic, engagement);
        // 潜力为两者加权平均，但因约束在 0-100，需允许端点情况
        assertTrue(potential >= min - 0.01 && potential <= max + 0.01,
                String.format("潜力 %.2f 应在 [%.2f, %.2f] 之间", potential, min, max));
    }

    @ParameterizedTest(name = "avg={0} fail={1} att={2} min={3} risk={4}")
    @MethodSource("metricCombos")
    @DisplayName("风险指数随风险概率单调不减")
    void testRiskMonotonic(double avg, int fail, double att, int minutes, double risk) {
        double low = service.computeDimensions(metrics(avg, fail, att, minutes, risk * 0.5)).get("risk").doubleValue();
        double high = service.computeDimensions(metrics(avg, fail, att, minutes, risk)).get("risk").doubleValue();
        assertTrue(high >= low - 0.01, "风险概率更高时风险指数不应下降");
    }

    @ParameterizedTest(name = "avg={0} fail={1} att={2} min={3} risk={4}")
    @MethodSource("metricCombos")
    @DisplayName("计算结果保留两位小数")
    void testScale(double avg, int fail, double att, int minutes, double risk) {
        service.computeDimensions(metrics(avg, fail, att, minutes, risk))
                .forEach((k, v) -> assertTrue(v.scale() <= 2, k + " 小数位应 <= 2"));
    }
}
