package com.gyb.xqfx.service;

import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.entity.WarningRule;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.mapper.WarningRuleMapper;
import com.gyb.xqfx.service.impl.ScoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 预警规则引擎测试（Mapper 使用 Mockito 隔离）
 */
@DisplayName("预警规则引擎测试")
class WarningServiceTest {

    private WarningRuleMapper ruleMapper;
    private WarningRecordMapper recordMapper;
    private WarningService service;

    @BeforeEach
    void setUp() {
        ruleMapper = Mockito.mock(WarningRuleMapper.class);
        recordMapper = Mockito.mock(WarningRecordMapper.class);
        StudentMapper studentMapper = Mockito.mock(StudentMapper.class);
        ClazzMapper clazzMapper = Mockito.mock(ClazzMapper.class);
        service = new WarningService(ruleMapper, recordMapper, studentMapper, clazzMapper,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Mockito.mock(WarningAnalyticsService.class));
    }

    private WarningRule rule(int level, String threshold, int enabled) {
        WarningRule r = new WarningRule();
        r.setId((long) level);
        r.setLevel(level);
        r.setRuleCode("R" + level);
        r.setRuleName("规则" + level);
        r.setScoreThreshold(new BigDecimal(threshold));
        r.setEnabled(enabled);
        return r;
    }

    /** 规则阈值为 0-100 分制：红80 橙60 黄40 蓝20 */
    private void mockRules() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(4, "80", 1), rule(3, "60", 1),
                rule(2, "40", 1), rule(1, "20", 1)));
    }

    @ParameterizedTest(name = "概率 {0} 命中等级 {1}")
    @CsvSource({
            "0.95, 4",
            "0.80, 4",
            "0.79, 3",
            "0.60, 3",
            "0.59, 2",
            "0.40, 2",
            "0.39, 1",
            "0.20, 1"
    })
    @DisplayName("按概率（换算为百分制后）命中最高等级规则")
    void testMatchRuleByProbability(String prob, int expectedLevel) {
        mockRules();
        WarningRule matched = service.matchRule(new BigDecimal(prob));
        assertNotNull(matched);
        assertEquals(expectedLevel, matched.getLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.14", "0.10", "0.0"})
    @DisplayName("概率过低不产生预警")
    void testNoWarningWhenLowProbability(String prob) {
        mockRules();
        assertNull(service.matchRule(new BigDecimal(prob)));
    }

    @Test
    @DisplayName("概率为 null 不产生预警")
    void testNullProbability() {
        assertNull(service.matchRule(null));
    }

    @Test
    @DisplayName("无启用规则时不产生预警")
    void testNoEnabledRules() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.matchRule(new BigDecimal("0.99")));
    }

    @Test
    @DisplayName("启用规则按等级降序排列")
    void testEnabledRulesSorted() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(1, "0.2", 1), rule(4, "0.85", 1), rule(2, "0.4", 1)));
        List<WarningRule> rules = service.enabledRules();
        assertEquals(4, rules.get(0).getLevel());
        assertEquals(2, rules.get(1).getLevel());
        assertEquals(1, rules.get(2).getLevel());
    }

    @Test
    @DisplayName("已存在同等或更高等级预警时被抑制")
    void testSuppressed() {
        WarningRecord existing = new WarningRecord();
        existing.setWarningLevel(3);
        existing.setStatus("ACTIVE");
        when(recordMapper.selectList(any())).thenReturn(List.of(existing));

        assertTrue(service.suppressed(1L, 1L, 3), "同等级应被抑制");
        assertTrue(service.suppressed(1L, 1L, 2), "更低等级应被抑制");
        assertFalse(service.suppressed(1L, 1L, 4), "更高等级不应被抑制，需要升级");
    }

    @Test
    @DisplayName("无历史预警时不抑制")
    void testNotSuppressedWhenEmpty() {
        when(recordMapper.selectList(any())).thenReturn(List.of());
        assertFalse(service.suppressed(1L, 1L, 1));
    }

    @Test
    @DisplayName("批量生成预警：仅高风险学生命中")
    void testGenerateFromPredictions() {
        mockRules();
        when(recordMapper.selectList(any())).thenReturn(List.of());

        PredictionResult high = new PredictionResult();
        high.setId(1L);
        high.setStudentId(101L);
        high.setRiskProbability(new BigDecimal("0.92"));
        high.setRiskLevel(4);

        PredictionResult low = new PredictionResult();
        low.setId(2L);
        low.setStudentId(102L);
        low.setRiskProbability(new BigDecimal("0.05"));
        low.setRiskLevel(1);

        assertEquals(1, service.generateFromPredictions(1L, List.of(high, low)),
                "仅高风险学生应生成预警");
    }

    @Test
    @DisplayName("全校低风险时不产生预警")
    void testGenerateNoneWhenAllLow() {
        mockRules();
        PredictionResult low = new PredictionResult();
        low.setStudentId(101L);
        low.setRiskProbability(new BigDecimal("0.10"));
        assertEquals(0, service.generateFromPredictions(1L, List.of(low)));
    }

    @Test
    @DisplayName("处理不存在的预警应抛业务异常")
    void testHandleMissingWarning() {
        when(recordMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.handle(999L, "HANDLED", 1L, "ok"));
    }

    @Test
    @DisplayName("处理预警应更新状态与责任人")
    void testHandleWarning() {
        WarningRecord record = new WarningRecord();
        record.setId(1L);
        record.setStatus("ACTIVE");
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(recordMapper.updateById(record)).thenReturn(1);

        assertTrue(service.handle(1L, "HANDLED", 7L, "已约谈"));
        assertEquals("HANDLED", record.getStatus());
        assertEquals(7L, record.getOwnerUserId());
        assertNotNull(record.getHandleTime());
    }

    @Test
    @DisplayName("等级统计按等级分组计数")
    void testStatistics() {
        WarningRecord a = new WarningRecord();
        a.setWarningLevel(4);
        WarningRecord b = new WarningRecord();
        b.setWarningLevel(4);
        WarningRecord c = new WarningRecord();
        c.setWarningLevel(2);
        when(recordMapper.selectList(any())).thenReturn(List.of(a, b, c));

        var stats = service.statistics(null, null);
        assertEquals(2L, stats.get(4));
        assertEquals(1L, stats.get(2));
    }

    @Test
    @DisplayName("类型统计按预警类型分组计数")
    void testTypeStatistics() {
        WarningRecord a = new WarningRecord();
        a.setWarningType("ACADEMIC");
        WarningRecord b = new WarningRecord();
        b.setWarningType("ACADEMIC");
        WarningRecord c = new WarningRecord();
        c.setWarningType("BEHAVIOR");
        WarningRecord d = new WarningRecord();
        d.setWarningType(null); // 未打类型的不计入
        when(recordMapper.selectList(any())).thenReturn(List.of(a, b, c, d));

        var stats = service.typeStatistics(1L, null);
        assertEquals(2L, stats.get("ACADEMIC"));
        assertEquals(1L, stats.get("BEHAVIOR"));
        assertEquals(2, stats.size());
    }

    @Test
    @DisplayName("生成预警按规则类型落库，缺省回退 ACADEMIC")
    void testGenerateWarningTypeFromRule() {
        mockRules();
        when(recordMapper.selectList(any())).thenReturn(List.of());
        WarningRule typed = rule(4, "80", 1);
        typed.setWarningType("PSYCH");
        when(ruleMapper.selectList(any())).thenReturn(List.of(typed));

        PredictionResult p = new PredictionResult();
        p.setId(1L);
        p.setStudentId(101L);
        p.setRiskProbability(new BigDecimal("0.92"));
        p.setRiskLevel(4);

        service.generateFromPredictions(1L, List.of(p));
        // 校验落库记录携带规则类型
        org.mockito.ArgumentCaptor<WarningRecord> captor = org.mockito.ArgumentCaptor.forClass(WarningRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals("PSYCH", captor.getValue().getWarningType());
    }
}
