package com.gyb.xqfx.service;

import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.client.RemoteLlmClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 提示词模板渲染数据驱动测试
 */
@DisplayName("提示词模板数据驱动测试")
class TemplateRenderDataDrivenTest {

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
                Mockito.mock(RemoteLlmClient.class),
                Mockito.mock(KnowledgeService.class),
                Mockito.mock(BigScreenService.class));
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        ReflectionTestUtils.setField(service, "fallbackToRule", true);
    }

    /** 生成 0~6 个占位符的模板 */
    static Stream<Arguments> templates() {
        List<Arguments> args = new ArrayList<>();
        String[] keys = {"a", "b", "c", "d", "e", "f"};
        for (int count = 0; count <= keys.length; count++) {
            StringBuilder sb = new StringBuilder("前缀-");
            for (int i = 0; i < count; i++) {
                sb.append("{").append(keys[i]).append("}").append("-");
            }
            sb.append("后缀");
            args.add(Arguments.of(sb.toString(), count));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "模板占位符数={1}")
    @MethodSource("templates")
    @DisplayName("全部变量提供时不残留占位符")
    void testNoPlaceholderLeft(String template, int count) {
        String[] keys = {"a", "b", "c", "d", "e", "f"};
        Map<String, Object> vars = new HashMap<>();
        for (int i = 0; i < count; i++) {
            vars.put(keys[i], "值" + i);
        }
        String result = service.renderTemplate(template, vars);
        assertFalse(result.contains("{"), "不应残留占位符：" + result);
        assertFalse(result.contains("}"), "不应残留占位符：" + result);
        assertTrue(result.startsWith("前缀-"));
        assertTrue(result.endsWith("后缀"));
    }

    @ParameterizedTest(name = "模板占位符数={1}")
    @MethodSource("templates")
    @DisplayName("缺少变量时占位符被替换为空串")
    void testMissingVariables(String template, int count) {
        String result = service.renderTemplate(template, new HashMap<>());
        assertFalse(result.contains("{"), "缺失变量应替换为空：" + result);
        assertEquals("前缀-" + "-".repeat(count) + "后缀", result);
    }

    static Stream<Arguments> numericValues() {
        List<Arguments> args = new ArrayList<>();
        for (int v : new int[]{0, -1, 1, 60, 100, 99999}) {
            args.add(Arguments.of(v));
        }
        for (double d : new double[]{0.0, 59.9, 60.0, 100.0}) {
            args.add(Arguments.of(d));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "数值变量={0}")
    @MethodSource("numericValues")
    @DisplayName("数值类型变量正确渲染")
    void testNumericValues(Object value) {
        String result = service.renderTemplate("平均分：{score}", Map.of("score", value));
        assertEquals("平均分：" + value, result);
    }

    static Stream<Arguments> nullishValues() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(""),
                Arguments.of("包含特殊字符 $ ^ ( ) [ ] \\"),
                Arguments.of("包含换行\n第二行"),
                Arguments.of("中文变量值"),
                Arguments.of("emoji-😀")
        );
    }

    @ParameterizedTest(name = "变量值=[{0}]")
    @MethodSource("nullishValues")
    @DisplayName("特殊字符与空值渲染不抛异常")
    void testSpecialValues(Object value) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("v", value);
        String result = assertDoesNotThrow(() -> service.renderTemplate("[{v}]", vars));
        assertNotNull(result);
        assertFalse(result.contains("{v}"));
    }
}
