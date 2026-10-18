package com.gyb.xqfx.service;

import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.mapper.AiCallLogMapper;
import com.gyb.xqfx.mapper.AiPromptTemplateMapper;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ReportRecordMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.service.AiAnalysisService.StudentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 大模型学情分析服务测试（纯逻辑部分，Mapper 全部 Mock）
 */
@DisplayName("AI 学情分析服务测试")
class AiAnalysisServiceTest {

    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(
                Mockito.mock(StudentMapper.class),
                Mockito.mock(ScoreMapper.class),
                Mockito.mock(AttendanceMapper.class),
                Mockito.mock(OnlineBehaviorMapper.class),
                Mockito.mock(PredictionResultMapper.class),
                Mockito.mock(WarningRecordMapper.class),
                Mockito.mock(TermMapper.class),
                Mockito.mock(ClazzMapper.class),
                Mockito.mock(AiPromptTemplateMapper.class),
                Mockito.mock(AiCallLogMapper.class),
                Mockito.mock(ReportRecordMapper.class),
                Mockito.mock(OllamaClient.class),
                Mockito.mock(KnowledgeService.class));
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        ReflectionTestUtils.setField(service, "fallbackToRule", true);
    }

    // ==================== 模板渲染 ====================

    @ParameterizedTest(name = "模板 {0} 渲染结果 {1}")
    @CsvSource({
            "hello, hello",
            "{name}, 张三",
            "a{name}b, a张三b",
            "{name}{name}, 张三张三"
    })
    @DisplayName("变量替换正确")
    void testRenderTemplate(String template, String expected) {
        String result = service.renderTemplate(template, Map.of("name", "张三"));
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("未提供变量时替换为空串")
    void testRenderMissingVariable() {
        assertEquals("你好，", service.renderTemplate("你好，{name}", Map.of()));
    }

    @Test
    @DisplayName("模板为 null 时返回空串")
    void testRenderNull() {
        assertEquals("", service.renderTemplate(null, Map.of()));
    }

    @Test
    @DisplayName("多变量同时替换")
    void testRenderMultiple() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", "1");
        vars.put("b", "2");
        assertEquals("1-2", service.renderTemplate("{a}-{b}", vars));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "无占位符的模板", "{a}{b}{c}"})
    @DisplayName("渲染不应抛异常")
    void testRenderNoException(String template) {
        assertDoesNotThrow(() -> service.renderTemplate(template, Map.of()));
    }

    // ==================== 规则化兜底 ====================

    private StudentContext goodStudent() {
        StudentContext ctx = new StudentContext();
        ctx.setStudentName("张三");
        ctx.setStudentNo("2301132013");
        ctx.setClazzName("计科1班");
        ctx.setTermName("2026-2027学年第一学期");
        ctx.setCourseCount(8);
        ctx.setAvgScore(85.0);
        ctx.setMaxScore(96.0);
        ctx.setMinScore(72.0);
        ctx.setFailCount(0);
        ctx.setAttendanceTotal(20);
        ctx.setAbsentCount(0);
        ctx.setAttendanceRate(1.0);
        ctx.setStudyMinutes(900);
        ctx.setRiskProbability(0.1);
        ctx.setRiskLevel(1);
        ctx.setWarningCount(0);
        return ctx;
    }

    private StudentContext riskyStudent() {
        StudentContext ctx = new StudentContext();
        ctx.setStudentName("李四");
        ctx.setStudentNo("2301132099");
        ctx.setCourseCount(8);
        ctx.setAvgScore(52.0);
        ctx.setMaxScore(78.0);
        ctx.setMinScore(31.0);
        ctx.setFailCount(4);
        ctx.setAttendanceTotal(20);
        ctx.setAbsentCount(7);
        ctx.setAttendanceRate(0.65);
        ctx.setStudyMinutes(120);
        ctx.setRiskProbability(0.92);
        ctx.setRiskLevel(4);
        ctx.setWarningCount(2);
        ctx.setMaxWarningLevel(4);
        return ctx;
    }

    @Test
    @DisplayName("优秀学生报告应提示无显著风险")
    void testReportGoodStudent() {
        String report = service.buildRuleBasedReport(goodStudent());
        assertTrue(report.contains("全部课程合格"));
        assertTrue(report.contains("暂未发现显著风险点"));
        assertTrue(report.contains("在线学习投入较高"));
    }

    @Test
    @DisplayName("风险学生报告应列出风险点")
    void testReportRiskyStudent() {
        String report = service.buildRuleBasedReport(riskyStudent());
        assertTrue(report.contains("4 门不及格课程"));
        assertTrue(report.contains("出勤率偏低"));
        assertTrue(report.contains("预测风险概率较高"));
    }

    @Test
    @DisplayName("报告包含四个固定章节")
    void testReportSections() {
        String report = service.buildRuleBasedReport(riskyStudent());
        assertTrue(report.contains("## 一、总体评价"));
        assertTrue(report.contains("## 二、优势与亮点"));
        assertTrue(report.contains("## 三、风险点"));
        assertTrue(report.contains("## 四、改进建议"));
    }

    @Test
    @DisplayName("风险学生建议应含重修、出勤、帮扶")
    void testAdviceRiskyStudent() {
        String advice = service.buildRuleBasedAdvice(riskyStudent());
        assertTrue(advice.contains("重修计划"));
        assertTrue(advice.contains("出勤率偏低"));
        assertTrue(advice.contains("重点帮扶名单"));
    }

    @Test
    @DisplayName("优秀学生建议不为空")
    void testAdviceGoodStudent() {
        String advice = service.buildRuleBasedAdvice(goodStudent());
        assertFalse(advice.isBlank());
        assertTrue(advice.contains("学业状况良好") || advice.contains("保持"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 0.59, 0.6, 1.0})
    @DisplayName("不同风险概率下建议均非空")
    void testAdviceAlwaysNonEmpty(double probability) {
        StudentContext ctx = goodStudent();
        ctx.setRiskProbability(probability);
        assertFalse(service.buildRuleBasedAdvice(ctx).isBlank());
    }

    @Test
    @DisplayName("上下文文本包含关键信息")
    void testContextToText() {
        String text = riskyStudent().toText();
        assertTrue(text.contains("李四"));
        assertTrue(text.contains("2301132099"));
        assertTrue(text.contains("65.0%"));
        assertTrue(text.contains("红色"));
    }

    @Test
    @DisplayName("未知等级显示为未知")
    void testContextUnknownLevel() {
        StudentContext ctx = goodStudent();
        ctx.setRiskLevel(null);
        assertTrue(ctx.toText().contains("未知"));
    }
}
