package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.ExamPaper;
import com.gyb.xqfx.entity.ExamPractice;
import com.gyb.xqfx.entity.ExamQuestion;
import com.gyb.xqfx.entity.ExamQuestionScore;
import com.gyb.xqfx.mapper.AiCallLogMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.ExamPaperMapper;
import com.gyb.xqfx.mapper.ExamPracticeMapper;
import com.gyb.xqfx.mapper.ExamQuestionMapper;
import com.gyb.xqfx.mapper.ExamQuestionScoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 错题举一反三服务测试：本地模型生成、JSON 解析、规则降级与错题判定
 */
@DisplayName("举一反三服务测试")
class ExamPracticeServiceTest {

    private ExamPracticeMapper practiceMapper;
    private ExamPaperMapper paperMapper;
    private ExamQuestionMapper questionMapper;
    private ExamQuestionScoreMapper questionScoreMapper;
    private CourseOfferingMapper courseOfferingMapper;
    private CourseMapper courseMapper;
    private AiCallLogMapper aiCallLogMapper;
    private OllamaClient ollamaClient;
    private ExamPracticeService service;

    @BeforeEach
    void setUp() {
        practiceMapper = Mockito.mock(ExamPracticeMapper.class);
        paperMapper = Mockito.mock(ExamPaperMapper.class);
        questionMapper = Mockito.mock(ExamQuestionMapper.class);
        questionScoreMapper = Mockito.mock(ExamQuestionScoreMapper.class);
        courseOfferingMapper = Mockito.mock(CourseOfferingMapper.class);
        courseMapper = Mockito.mock(CourseMapper.class);
        aiCallLogMapper = Mockito.mock(AiCallLogMapper.class);
        ollamaClient = Mockito.mock(OllamaClient.class);
        service = new ExamPracticeService(practiceMapper, paperMapper, questionMapper, questionScoreMapper,
                courseOfferingMapper, courseMapper, aiCallLogMapper, ollamaClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "llmEnabled", true);
    }

    private ExamQuestion question(Long id, String type, String kp, BigDecimal full, BigDecimal rate) {
        ExamQuestion q = new ExamQuestion();
        q.setId(id);
        q.setExamPaperId(1L);
        q.setQuestionNo("Q" + id);
        q.setQuestionType(type);
        q.setKnowledgePoint(kp);
        q.setFullScore(full);
        q.setScoreRate(rate);
        q.setStem("下列关于「" + kp + "」的表述，正确的一项是？");
        q.setOptionsJson("[\"A. 定义正确\",\"B. 概念混淆\",\"C. 忽略前提\",\"D. 因果颠倒\"]");
        q.setAnswer("A");
        return q;
    }

    private void mockCourse() {
        ExamPaper paper = new ExamPaper();
        paper.setCourseOfferingId(10L);
        Mockito.when(paperMapper.selectById(1L)).thenReturn(paper);
        CourseOffering offering = new CourseOffering();
        offering.setCourseId(100L);
        Mockito.when(courseOfferingMapper.selectById(10L)).thenReturn(offering);
        Course course = new Course();
        course.setCourseName("数据结构");
        Mockito.when(courseMapper.selectById(100L)).thenReturn(course);
    }

    // ==================== JSON 解析 ====================

    @Test
    @DisplayName("解析模型输出的练习题数组")
    void testParseQuestions() {
        String json = """
                [
                  {"stem":"题干1","options":["A. a","B. b"],"answer":"A","analysis":"解析1","difficulty":"EASY"},
                  {"stem":"题干2","options":[],"answer":"正确","analysis":"解析2","difficulty":"MEDIUM"}
                ]
                """;
        List<ExamPracticeService.PracticeQuestion> items = service.parseQuestions(json);
        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals(1, items.get(0).getIndex());
        assertEquals("题干1", items.get(0).getStem());
        assertEquals(2, items.get(0).getOptions().size());
        assertEquals("A", items.get(0).getAnswer());
        assertEquals("EASY", items.get(0).getDifficulty());
        assertTrue(items.get(1).getOptions().isEmpty());
    }

    @Test
    @DisplayName("带 ```json 围栏的输出也能解析")
    void testParseWithFence() {
        String text = "```json\n[{\"stem\":\"围栏题\",\"answer\":\"B\"}]\n```";
        List<ExamPracticeService.PracticeQuestion> items = service.parseQuestions(text);
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("围栏题", items.get(0).getStem());
    }

    @Test
    @DisplayName("模型输出前后夹杂说明文字时截取数组部分")
    void testParseWithNoise() {
        String text = "好的，以下是练习题：\n[{\"stem\":\"噪声题\",\"answer\":\"C\"}]\n希望对你有帮助。";
        List<ExamPracticeService.PracticeQuestion> items = service.parseQuestions(text);
        assertNotNull(items);
        assertEquals("噪声题", items.get(0).getStem());
    }

    @Test
    @DisplayName("按标记式文本解析模型输出")
    void testParseMarked() {
        String text = """
                ====
                【题干】下列关于栈的说法正确的是？
                【选项】A. 后进先出；B. 先进先出；C. 随机访问；D. 只能顺序存储
                【答案】A
                【解析】栈是后进先出的线性表，注意与队列区分。
                【难度】EASY
                ====
                【题干】简述队列与栈的区别。
                【选项】无
                【答案】见解析要点
                【解析】两者都是线性表，区别在于插入删除的位置不同。
                【难度】困难
                """;
        List<ExamPracticeService.PracticeQuestion> items = service.parseQuestions(text);
        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals("下列关于栈的说法正确的是？", items.get(0).getStem());
        assertEquals(4, items.get(0).getOptions().size());
        assertEquals("A. 后进先出", items.get(0).getOptions().get(0));
        assertEquals("A", items.get(0).getAnswer());
        assertEquals("EASY", items.get(0).getDifficulty());
        assertTrue(items.get(1).getOptions().isEmpty(), "非选择题选项应为空");
        assertEquals("HARD", items.get(1).getDifficulty(), "中文难度应归一化");
    }

    @Test
    @DisplayName("模型输出 JSON 括号错位时仍能由标记式通道兜底")
    void testParseFallbackToMarked() {
        String badJson = "[{\"stem\":\"坏JSON\",\"analysis\":\"多了括号\"],\"answer\":\"A\"}]";
        assertNull(service.parseQuestions(badJson), "纯坏 JSON 无法解析时应降级");
    }

    @Test
    @DisplayName("非法输出返回 null 以便降级")
    void testParseInvalid() {
        assertNull(service.parseQuestions("这不是 JSON"));
        assertNull(service.parseQuestions(""));
        assertNull(service.parseQuestions(null));
        assertNull(service.parseQuestions("[{\"options\":[]}]"), "缺少题干应视为无效");
    }

    // ==================== 提示词 ====================

    @Test
    @DisplayName("提示词包含原题题干、选项、答案与知识点")
    void testBuildPrompt() {
        ExamQuestion q = question(1L, "SINGLE", "二叉树遍历", new BigDecimal("5"), new BigDecimal("45"));
        String prompt = service.buildPrompt(q, "数据结构", new BigDecimal("2"), 3);
        assertTrue(prompt.contains("二叉树遍历"));
        assertTrue(prompt.contains("单选题"));
        assertTrue(prompt.contains("下列关于「二叉树遍历」的表述"));
        assertTrue(prompt.contains("A. 定义正确"), "应带上原题选项");
        assertTrue(prompt.contains("参考答案：A"));
        assertTrue(prompt.contains("该生得分：2"));
        assertTrue(prompt.contains("生成 3 道同类练习题"));
        assertTrue(prompt.contains("不得重复原题"));
    }

    // ==================== 生成链路 ====================

    @Test
    @DisplayName("本地模型可用时生成 AI 练习题")
    void testGenerateWithLlm() {
        mockCourse();
        Mockito.when(questionMapper.selectById(1L))
                .thenReturn(question(1L, "SINGLE", "栈与队列", new BigDecimal("5"), new BigDecimal("45")));
        OllamaClient.ChatResult chat = new OllamaClient.ChatResult();
        chat.model = "llama3.2:3b";
        chat.content = "[{\"stem\":\"同类题1\",\"options\":[\"A. x\",\"B. y\"],\"answer\":\"A\","
                + "\"analysis\":\"解析\",\"difficulty\":\"EASY\"}]";
        chat.promptTokens = 100;
        chat.completionTokens = 200;
        Mockito.when(ollamaClient.chatWithModel(any(), any(), any())).thenReturn(chat);
        Mockito.doAnswer(inv -> {
            ExamPractice p = inv.getArgument(0);
            p.setId(11L);
            return 1;
        }).when(practiceMapper).insert(any(ExamPractice.class));

        ExamPracticeService.PracticeResult r = service.generate(1L, 1L, 5L, 3);
        assertEquals("AI", r.getSourceType());
        assertEquals("llama3.2:3b", r.getModel());
        assertEquals("SUCCESS", r.getStatus());
        assertEquals(1, r.getItems().size());
        assertEquals("同类题1", r.getItems().get(0).getStem());
        assertEquals("栈与队列", r.getKnowledgePoint());
        assertEquals("数据结构", r.getCourseName());
        Mockito.verify(aiCallLogMapper).insert(any(com.gyb.xqfx.entity.AiCallLog.class));
    }

    @Test
    @DisplayName("模型不可用时降级为规则生成并标记 FALLBACK")
    void testGenerateFallback() {
        mockCourse();
        Mockito.when(questionMapper.selectById(1L))
                .thenReturn(question(1L, "FILL", "时间复杂度", new BigDecimal("10"), new BigDecimal("50")));
        Mockito.when(ollamaClient.chatWithModel(any(), any(), any())).thenReturn(null);

        ExamPracticeService.PracticeResult r = service.generate(1L, 1L, 5L, 2);
        assertEquals("RULE", r.getSourceType());
        assertEquals("FALLBACK", r.getStatus());
        assertEquals(2, r.getItems().size());
        assertTrue(r.getItems().get(0).getStem().contains("本地模型暂不可用"));
        assertTrue(r.getItems().get(0).getStem().contains("时间复杂度"));
        assertEquals("EASY", r.getItems().get(0).getDifficulty());
    }

    @Test
    @DisplayName("模型返回无法解析内容时同样降级")
    void testGenerateFallbackOnBadJson() {
        mockCourse();
        Mockito.when(questionMapper.selectById(1L))
                .thenReturn(question(1L, "SINGLE", "图论", new BigDecimal("5"), new BigDecimal("40")));
        OllamaClient.ChatResult chat = new OllamaClient.ChatResult();
        chat.model = "qwen2.5";
        chat.content = "抱歉，我无法生成题目。";
        Mockito.when(ollamaClient.chatWithModel(any(), any(), any())).thenReturn(chat);

        ExamPracticeService.PracticeResult r = service.generate(1L, 1L, null, 3);
        assertEquals("RULE", r.getSourceType());
        assertEquals("FALLBACK", r.getStatus());
        assertEquals(3, r.getItems().size());
    }

    @Test
    @DisplayName("未启用本地模型时直接走规则生成")
    void testGenerateLlmDisabled() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        mockCourse();
        Mockito.when(questionMapper.selectById(1L))
                .thenReturn(question(1L, "SHORT", "排序算法", new BigDecimal("15"), new BigDecimal("55")));

        ExamPracticeService.PracticeResult r = service.generate(1L, 1L, 7L, 2);
        assertEquals("RULE", r.getSourceType());
        Mockito.verify(ollamaClient, Mockito.never()).chatWithModel(any(), any(), any());
    }

    @Test
    @DisplayName("生成题数受上限约束")
    void testGenerateCountLimit() {
        mockCourse();
        Mockito.when(questionMapper.selectById(1L))
                .thenReturn(question(1L, "SINGLE", "哈希表", new BigDecimal("5"), new BigDecimal("40")));
        Mockito.when(ollamaClient.chatWithModel(any(), any(), any())).thenReturn(null);

        assertEquals(3, service.generate(1L, 1L, 1L, null).getItems().size(), "默认 3 道");
        assertEquals(6, service.generate(1L, 1L, 1L, 99).getItems().size(), "上限 6 道");
        assertEquals(1, service.generate(1L, 1L, 1L, 0).getItems().size(), "下限 1 道");
    }

    @Test
    @DisplayName("题目不存在时抛出异常")
    void testGenerateQuestionMissing() {
        Mockito.when(questionMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generate(1L, 999L, null, 3));
    }

    // ==================== 错题判定 ====================

    @Test
    @DisplayName("班级维度只返回得分率低于60%的薄弱题")
    void testWeakQuestions() {
        Mockito.when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                question(1L, "SINGLE", "概念A", new BigDecimal("5"), new BigDecimal("80")),
                question(2L, "SINGLE", "概念B", new BigDecimal("5"), new BigDecimal("45")),
                question(3L, "FILL", "概念C", new BigDecimal("10"), new BigDecimal("55"))));

        List<ExamPracticeService.WrongQuestion> list = service.weakQuestionsOfPaper(1L);
        assertEquals(2, list.size(), "80% 得分率的题不应作为薄弱题");
        assertEquals("概念B", list.get(0).getKnowledgePoint(), "按得分率升序，最低的在前");
        assertEquals("概念C", list.get(1).getKnowledgePoint());
    }

    @Test
    @DisplayName("学生维度只返回未得满分的题目")
    void testStudentWrongQuestions() {
        ExamQuestion q1 = question(1L, "SINGLE", "概念A", new BigDecimal("5"), new BigDecimal("70"));
        ExamQuestion q2 = question(2L, "SINGLE", "概念B", new BigDecimal("5"), new BigDecimal("50"));
        Mockito.when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(q1, q2));

        ExamQuestionScore s1 = new ExamQuestionScore();
        s1.setExamQuestionId(1L);
        s1.setScore(new BigDecimal("5"));
        ExamQuestionScore s2 = new ExamQuestionScore();
        s2.setExamQuestionId(2L);
        s2.setScore(new BigDecimal("1"));
        Mockito.when(questionScoreMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s1, s2));

        List<ExamPracticeService.WrongQuestion> list = service.wrongQuestionsOfStudent(1L, 5L);
        assertEquals(1, list.size(), "满分的题不算错题");
        assertEquals("概念B", list.get(0).getKnowledgePoint());
        assertEquals(0, new BigDecimal("1.00").compareTo(list.get(0).getStudentScore()));
        assertEquals(0, new BigDecimal("4.00").compareTo(list.get(0).getLostScore()));
    }

    // ==================== 查询与详情 ====================

    @Test
    @DisplayName("练习详情反序列化回题目列表")
    void testDetail() {
        ExamPractice p = new ExamPractice();
        p.setId(11L);
        p.setExamQuestionId(1L);
        p.setStudentId(5L);
        p.setKnowledgePoint("栈");
        p.setSourceType("AI");
        p.setStatus("SUCCESS");
        p.setContentJson("[{\"stem\":\"练习1\",\"options\":[\"A. a\"],\"answer\":\"A\"}]");
        Mockito.when(practiceMapper.selectById(11L)).thenReturn(p);

        ExamPracticeService.PracticeResult r = service.detail(11L);
        assertEquals(1, r.getItems().size());
        assertEquals("练习1", r.getItems().get(0).getStem());
    }

    @Test
    @DisplayName("练习不存在时抛出异常")
    void testDetailMissing() {
        Mockito.when(practiceMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detail(404L));
    }

    @Test
    @DisplayName("学生练习列表与题目历史记录")
    void testLists() {
        ExamPractice a = new ExamPractice();
        a.setId(1L);
        a.setStudentId(5L);
        a.setQuestionCount(3);
        ExamPractice b = new ExamPractice();
        b.setId(2L);
        b.setExamQuestionId(1L);
        b.setQuestionCount(2);
        Mockito.when(practiceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(a), List.of(b));

        List<ExamPracticeService.PracticeBrief> mine = service.myPractices(5L);
        List<ExamPracticeService.PracticeBrief> history = service.practicesOfQuestion(1L);
        assertEquals(1, mine.size());
        assertEquals(1, history.size());
        assertEquals(3, mine.get(0).getQuestionCount());
        assertEquals(2, history.get(0).getQuestionCount());
        assertEquals(1L, history.get(0).getQuestionId());
    }
}
