package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ReportRecordMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.DashboardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 系统概览服务测试
 */
@DisplayName("系统概览服务测试")
class DashboardServiceTest {

    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private ClazzMapper clazzMapper;
    private PredictionResultMapper predictionResultMapper;
    private WarningRecordMapper warningRecordMapper;
    private ReportRecordMapper reportRecordMapper;
    private InterventionTicketMapper ticketMapper;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        clazzMapper = Mockito.mock(ClazzMapper.class);
        predictionResultMapper = Mockito.mock(PredictionResultMapper.class);
        warningRecordMapper = Mockito.mock(WarningRecordMapper.class);
        reportRecordMapper = Mockito.mock(ReportRecordMapper.class);
        ticketMapper = Mockito.mock(InterventionTicketMapper.class);
        service = new DashboardService(studentMapper, scoreMapper, clazzMapper,
                predictionResultMapper, warningRecordMapper, reportRecordMapper, ticketMapper);
    }

    // ==================== 成绩分布 ====================

    @Test
    @DisplayName("分数段分布按固定顺序输出")
    void testScoreDistribution() {
        Map<String, Object> seg = new HashMap<>();
        seg.put("seg1", 5L);
        seg.put("seg2", 10L);
        seg.put("seg3", 20L);
        seg.put("seg4", 30L);
        seg.put("seg5", 15L);
        Mockito.when(scoreMapper.statSegments(any())).thenReturn(seg);

        List<DashboardVO.NameValue> list = service.scoreDistribution(null);
        assertEquals(5, list.size());
        assertEquals("60分以下", list.get(0).getName());
        assertEquals(5L, list.get(0).getValue());
        assertEquals("90分及以上", list.get(4).getName());
        assertEquals(15L, list.get(4).getValue());
    }

    @Test
    @DisplayName("统计异常时分数段补 0 而非报错")
    void testScoreDistributionOnError() {
        Mockito.when(scoreMapper.statSegments(any())).thenThrow(new RuntimeException("DB 异常"));
        List<DashboardVO.NameValue> list = service.scoreDistribution(null);
        assertEquals(5, list.size());
        list.forEach(v -> assertEquals(0L, v.getValue()));
    }

    @Test
    @DisplayName("返回 null 结果时安全降级")
    void testScoreDistributionNull() {
        Mockito.when(scoreMapper.statSegments(any())).thenReturn(null);
        assertEquals(5, service.scoreDistribution(null).size());
    }

    // ==================== 预警分布 ====================

    @Test
    @DisplayName("缺失等级补 0，保证四个等级齐全")
    void testWarningDistribution() {
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of(
                Map.of("level", 4, "cnt", 8L),
                Map.of("level", 1, "cnt", 3L)));

        List<DashboardVO.NameValue> list = service.warningDistribution();
        assertEquals(4, list.size());
        assertEquals("蓝色", list.get(0).getName());
        assertEquals(3L, list.get(0).getValue());
        assertEquals("黄色", list.get(1).getName());
        assertEquals(0L, list.get(1).getValue());
        assertEquals("红色", list.get(3).getName());
        assertEquals(8L, list.get(3).getValue());
    }

    @Test
    @DisplayName("无预警时四个等级均为 0")
    void testWarningDistributionEmpty() {
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of());
        List<DashboardVO.NameValue> list = service.warningDistribution();
        assertEquals(4, list.size());
        list.forEach(v -> assertEquals(0L, v.getValue()));
    }

    @Test
    @DisplayName("等级为 null 的行被跳过")
    void testWarningDistributionNullLevel() {
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of(Map.of("cnt", 5L)));
        assertEquals(4, service.warningDistribution().size());
    }

    // ==================== 班级排名 ====================

    @Test
    @DisplayName("班级排名附带预警人数")
    void testClazzRanking() {
        DashboardVO.ClazzStat a = new DashboardVO.ClazzStat();
        a.setClazzId(1L);
        a.setClazzName("计科2101");
        DashboardVO.ClazzStat b = new DashboardVO.ClazzStat();
        b.setClazzId(2L);
        b.setClazzName("计科2102");
        Mockito.when(scoreMapper.statByClazz(any())).thenReturn(List.of(a, b));
        Mockito.when(warningRecordMapper.countByClazz()).thenReturn(List.of(Map.of("clazzId", 2L, "cnt", 6L)));

        List<DashboardVO.ClazzStat> ranking = service.clazzRanking(null);
        assertEquals(2, ranking.size());
        assertEquals(0L, ranking.get(0).getWarningCount());
        assertEquals(6L, ranking.get(1).getWarningCount());
    }

    @Test
    @DisplayName("班级对比保留两位小数")
    void testClazzComparisonScale() {
        DashboardVO.ClazzStat stat = new DashboardVO.ClazzStat();
        stat.setClazzId(1L);
        stat.setAvgScore(new BigDecimal("78.123456"));
        stat.setPassRate(new BigDecimal("90.5678"));
        stat.setExcellentRate(new BigDecimal("12.3456"));
        Mockito.when(scoreMapper.statByClazz(any())).thenReturn(List.of(stat));
        Mockito.when(warningRecordMapper.countByClazz()).thenReturn(List.of());

        DashboardVO.ClazzStat result = service.clazzComparison(null).get(0);
        assertEquals(2, result.getAvgScore().scale());
        assertEquals(2, result.getPassRate().scale());
        assertEquals(2, result.getExcellentRate().scale());
    }

    // ==================== 概览 ====================

    @Test
    @DisplayName("概览聚合各模块指标并按记录数加权")
    void testOverview() {
        Mockito.when(studentMapper.selectCount(any())).thenReturn(50L);
        Mockito.when(clazzMapper.selectCount(any())).thenReturn(2L);
        Mockito.when(scoreMapper.selectCount(any())).thenReturn(1200L);
        Mockito.when(predictionResultMapper.selectCount(any())).thenReturn(50L);
        Mockito.when(warningRecordMapper.selectCount(any())).thenReturn(12L);
        Mockito.when(reportRecordMapper.selectCount(any())).thenReturn(3L);
        Mockito.when(ticketMapper.selectCount(any())).thenReturn(4L);

        DashboardVO.TermStat t1 = new DashboardVO.TermStat();
        t1.setTermId(1L);
        t1.setTermName("2023-2024-1");
        t1.setScoreCount(400L);
        t1.setAvgScore(new BigDecimal("70"));
        t1.setPassRate(new BigDecimal("80"));
        DashboardVO.TermStat t2 = new DashboardVO.TermStat();
        t2.setTermId(2L);
        t2.setTermName("2023-2024-2");
        t2.setScoreCount(600L);
        t2.setAvgScore(new BigDecimal("80"));
        t2.setPassRate(new BigDecimal("90"));
        Mockito.when(scoreMapper.statByTerm()).thenReturn(List.of(t1, t2));
        Mockito.when(scoreMapper.statSegments(any())).thenReturn(Map.of("seg5", 100L));
        Mockito.when(scoreMapper.statByClazz(any())).thenReturn(List.of());
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of());
        Mockito.when(warningRecordMapper.countByClazz()).thenReturn(List.of());

        DashboardVO vo = service.overview(null);
        assertEquals(50L, vo.getStudentCount());
        assertEquals(12L, vo.getActiveWarningCount());
        assertEquals(4L, vo.getOpenTicketCount());
        assertEquals(2, vo.getTermTrend().size());
        // 加权平均：(70*400 + 80*600)/1000 = 76
        assertEquals(0, new BigDecimal("76.00").compareTo(vo.getAvgScore()));
        assertEquals(0, new BigDecimal("86.00").compareTo(vo.getPassRate()));
    }

    @Test
    @DisplayName("无成绩数据时概览指标为 0 不报错")
    void testOverviewEmpty() {
        Mockito.when(studentMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(clazzMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(scoreMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(predictionResultMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(warningRecordMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(reportRecordMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(ticketMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(scoreMapper.statByTerm()).thenReturn(List.of());
        Mockito.when(scoreMapper.statSegments(any())).thenReturn(Map.of());
        Mockito.when(scoreMapper.statByClazz(any())).thenReturn(List.of());
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of());
        Mockito.when(warningRecordMapper.countByClazz()).thenReturn(List.of());

        DashboardVO vo = service.overview(null);
        assertEquals(0L, vo.getStudentCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getAvgScore()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getPassRate()));
    }

    @Test
    @DisplayName("学期统计异常时概览仍可返回")
    void testOverviewTermError() {
        Mockito.when(studentMapper.selectCount(any())).thenReturn(10L);
        Mockito.when(scoreMapper.statByTerm()).thenThrow(new RuntimeException("DB 异常"));
        Mockito.when(scoreMapper.statSegments(any())).thenReturn(Map.of());
        Mockito.when(warningRecordMapper.statByLevel()).thenReturn(List.of());
        Mockito.when(scoreMapper.statByClazz(any())).thenReturn(List.of());
        Mockito.when(warningRecordMapper.countByClazz()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.overview(null));
    }
}
