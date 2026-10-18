package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.entity.PaperScan;
import com.gyb.xqfx.entity.QuestionBank;
import com.gyb.xqfx.mapper.PaperScanMapper;
import com.gyb.xqfx.mapper.QuestionBankMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 试卷录入服务：AI 解析入库 / 题库编辑删除 / 一键布置作业
 */
@ExtendWith(MockitoExtension.class)
class PaperScanServiceTest {

    @Mock
    private PaperScanMapper paperScanMapper;
    @Mock
    private QuestionBankMapper questionBankMapper;
    @Mock
    private HomeworkService homeworkService;
    @Mock
    private OllamaClient ollamaClient;

    @InjectMocks
    private PaperScanService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        var field = PaperScanService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, objectMapper);
        var uploadDir = PaperScanService.class.getDeclaredField("uploadDir");
        uploadDir.setAccessible(true);
        uploadDir.set(service, "./upload/scan");
    }

    private PaperScan scanOf(Long id, String text, String status) {
        PaperScan s = new PaperScan();
        s.setId(id);
        s.setTitle("测试试卷");
        s.setOcrText(text);
        s.setStatus(status);
        s.setQuestionCount(0);
        return s;
    }

    @Test
    void parseAndAnalyze_writesQuestionsAndSummary() {
        PaperScan scan = scanOf(1L, "1. 单选 函数 f(x)=x^2 的导数是？ A.2x B.x", "PARSED");
        when(paperScanMapper.selectById(1L)).thenReturn(scan);

        String questionsJson = """
                [{"no":"1","type":"SINGLE","stem":"函数 f(x)=x^2 的导数是？","options":["A. 2x","B. x"],"answer":"A","analysis":"幂函数求导","score":10,"knowledgePoint":"导数"},
                 {"no":"2","type":"FILL","stem":"函数 f(x)=e^x 的导数是____","options":[],"answer":"e^x","analysis":"指数函数求导","score":10,"knowledgePoint":"导数"}]""";
        when(ollamaClient.chatWithModel(anyString(), anyString(), anyString()))
                .thenReturn(result("llama3.1:8b", questionsJson))
                .thenReturn(result("llama3.1:8b", "{\"difficulty\":0.6,\"summary\":\"整体偏基础，导数运算为主\"}"));

        AtomicLong idGen = new AtomicLong(100);
        when(questionBankMapper.insert(org.mockito.ArgumentMatchers.<QuestionBank>any())).thenAnswer(inv -> {
            QuestionBank q = inv.getArgument(0);
            q.setId(idGen.getAndIncrement());
            return 1;
        });

        PaperScan out = service.parseAndAnalyze(1L);

        verify(questionBankMapper, times(2)).insert(org.mockito.ArgumentMatchers.<QuestionBank>any());
        verify(paperScanMapper).updateById(scan);
        assertEquals("REVIEWED", out.getStatus());
        assertEquals(2, out.getQuestionCount());
        assertEquals("整体偏基础，导数运算为主", out.getAiSummary());
        assertNotNull(out.getDifficulty());
        assertEquals(0, out.getDifficulty().compareTo(java.math.BigDecimal.valueOf(0.6)));
    }

    @Test
    void parseAndAnalyze_noQuestions_fails() {
        PaperScan scan = scanOf(2L, "没有题目内容的文本", "PARSED");
        when(paperScanMapper.selectById(2L)).thenReturn(scan);
        when(ollamaClient.chatWithModel(anyString(), anyString(), anyString()))
                .thenReturn(result("llama3.1:8b", "[]"));

        assertThrows(com.gyb.xqfx.common.BusinessException.class, () -> service.parseAndAnalyze(2L));
        verify(questionBankMapper, never()).insert(org.mockito.ArgumentMatchers.<QuestionBank>any());
    }

    @Test
    void parseAndAnalyze_emptyText_fails() {
        PaperScan scan = scanOf(3L, "  ", "PARSED");
        when(paperScanMapper.selectById(3L)).thenReturn(scan);
        assertThrows(com.gyb.xqfx.common.BusinessException.class, () -> service.parseAndAnalyze(3L));
    }

    @Test
    void bankEdit_updatesFields() {
        QuestionBank q = new QuestionBank();
        q.setId(5L);
        q.setStem("旧题干");
        when(questionBankMapper.selectById(5L)).thenReturn(q);

        service.bankEdit(5L, "新题干", "A", "解析", java.math.BigDecimal.valueOf(5), "极限", "难", "SINGLE", "[]");

        assertEquals("新题干", q.getStem());
        assertEquals("A", q.getAnswer());
        assertEquals("极限", q.getKnowledgePoint());
        assertEquals("难", q.getDifficulty());
        assertEquals("SINGLE", q.getQuestionType());
        verify(questionBankMapper).updateById(q);
    }

    @Test
    void bankDelete_removes() {
        QuestionBank q = new QuestionBank();
        q.setId(6L);
        when(questionBankMapper.selectById(6L)).thenReturn(q);
        service.bankDelete(6L);
        verify(questionBankMapper).deleteById(6L);
    }

    @Test
    void deleteScan_cascadesQuestions() {
        PaperScan scan = scanOf(9L, "text", "REVIEWED");
        scan.setFilePath("./upload/scan/x.docx");
        when(paperScanMapper.selectById(9L)).thenReturn(scan);
        service.deleteScan(9L, 1L);
        verify(questionBankMapper).delete(any());
        verify(paperScanMapper).deleteById(9L);
    }

    @Test
    void toHomework_buildsQuestionsFromBank() throws Exception {
        QuestionBank q1 = new QuestionBank();
        q1.setId(10L);
        q1.setQuestionNo("1");
        q1.setQuestionType("SINGLE");
        q1.setStem("题干1");
        q1.setOptionsJson(objectMapper.writeValueAsString(List.of("A. x", "B. y")));
        q1.setAnswer("A");
        q1.setScore(java.math.BigDecimal.valueOf(5));
        q1.setKnowledgePoint("导数");

        QuestionBank q2 = new QuestionBank();
        q2.setId(11L);
        q2.setQuestionNo("2");
        q2.setQuestionType("ESSAY");
        q2.setStem("题干2");
        q2.setOptionsJson("[]");
        q2.setAnswer("略");
        q2.setScore(java.math.BigDecimal.valueOf(15));

        when(questionBankMapper.selectBatchIds(List.of(10L, 11L))).thenReturn(List.of(q1, q2));
        when(homeworkService.create(anyLong(), anyString(), any(), any(), any(), anyList(), any()))
                .thenReturn(77L);

        Long hwId = service.toHomework(1L, List.of(10L, 11L), "测试作业", 1L, "高数", 2L, null);

        assertEquals(77L, hwId);
        org.mockito.ArgumentCaptor<java.util.List> captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(homeworkService).create(eq(1L), eq("测试作业"), eq(1L), eq("高数"), eq(2L), captor.capture(), isNull());
        java.util.List<?> qs = captor.getValue();
        assertEquals(2, qs.size());
        var hq = (HomeworkService.Question) qs.get(0);
        assertEquals("CHOICE", hq.type);
        assertTrue(hq.content.contains("A. x"));
        var eq = (HomeworkService.Question) qs.get(1);
        assertEquals("ESSAY", eq.type);
    }

    private OllamaClient.ChatResult result(String model, String content) {
        OllamaClient.ChatResult r = new OllamaClient.ChatResult();
        r.model = model;
        r.content = content;
        return r;
    }
}
