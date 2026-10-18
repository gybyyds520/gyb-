package com.gyb.xqfx.service;

import com.gyb.xqfx.entity.ExamPaper;
import com.gyb.xqfx.entity.ExamQuestion;
import com.gyb.xqfx.entity.ExamQuestionScore;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.mapper.ExamPaperMapper;
import com.gyb.xqfx.mapper.ExamQuestionMapper;
import com.gyb.xqfx.mapper.ExamQuestionScoreMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.model.vo.ExamAnalysisVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 试卷分析服务测试
 */
@DisplayName("试卷分析服务测试")
class ExamServiceTest {

    private ExamPaperMapper paperMapper;
    private ExamQuestionMapper questionMapper;
    private ExamQuestionScoreMapper scoreMapper;
    private ScoreMapper courseScoreMapper;
    private ExamService service;

    @BeforeEach
    void setUp() {
        paperMapper = Mockito.mock(ExamPaperMapper.class);
        questionMapper = Mockito.mock(ExamQuestionMapper.class);
        scoreMapper = Mockito.mock(ExamQuestionScoreMapper.class);
        courseScoreMapper = Mockito.mock(ScoreMapper.class);
        service = new ExamService(paperMapper, questionMapper, scoreMapper, courseScoreMapper);
    }

    private ExamQuestion question(Long id, String no, String point, String full) {
        ExamQuestion q = new ExamQuestion();
        q.setId(id);
        q.setQuestionNo(no);
        q.setKnowledgePoint(point);
        q.setFullScore(new BigDecimal(full));
        return q;
    }

    private ExamQuestionScore answer(Long paperId, Long questionId, Long studentId, String score) {
        ExamQuestionScore s = new ExamQuestionScore();
        s.setExamPaperId(paperId);
        s.setExamQuestionId(questionId);
        s.setStudentId(studentId);
        s.setScore(new BigDecimal(score));
        return s;
    }

    // ==================== 基础统计 ====================

    @Test
    @DisplayName("均值计算正确")
    void testMean() {
        assertEquals(0, new BigDecimal("6.50").compareTo(
                service.mean(List.of(new BigDecimal("5"), new BigDecimal("8")))));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.mean(List.of())));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.mean(null)));
    }

    @ParameterizedTest
    @CsvSource({"1,1,0.000", "1,5,2.000", "0,10,5.000"})
    @DisplayName("标准差计算正确")
    void testStdDev(String a, String b, String expected) {
        BigDecimal result = service.stdDev(List.of(new BigDecimal(a), new BigDecimal(b)));
        assertEquals(0, new BigDecimal(expected).compareTo(result));
    }

    @Test
    @DisplayName("样本不足两条时标准差为 0")
    void testStdDevSingle() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.stdDev(List.of(new BigDecimal("5")))));
    }

    // ==================== 掌握度 ====================

    @ParameterizedTest
    @CsvSource({"90, 掌握较好", "85, 掌握较好", "84.99, 一般", "60, 一般", "59.99, 薄弱", "0, 薄弱"})
    @DisplayName("掌握度评级边界正确")
    void testEvalMastery(String rate, String expected) {
        assertEquals(expected, service.evalMastery(new BigDecimal(rate)));
    }

    @Test
    @DisplayName("null 掌握度视为薄弱")
    void testEvalMasteryNull() {
        assertEquals("薄弱", service.evalMastery(null));
    }

    @Test
    @DisplayName("知识点按掌握度升序排列，薄弱点在前")
    void testBuildKnowledgeSorted() {
        ExamAnalysisVO.QuestionStat good = new ExamAnalysisVO.QuestionStat();
        good.setKnowledgePoint("基本概念");
        good.setFullScore(new BigDecimal("10"));
        good.setAvgScore(new BigDecimal("9"));

        ExamAnalysisVO.QuestionStat weak = new ExamAnalysisVO.QuestionStat();
        weak.setKnowledgePoint("综合应用");
        weak.setFullScore(new BigDecimal("10"));
        weak.setAvgScore(new BigDecimal("4"));

        List<ExamAnalysisVO.KnowledgeMastery> result = service.buildKnowledge(List.of(good, weak));
        assertEquals(2, result.size());
        assertEquals("综合应用", result.get(0).getKnowledgePoint());
        assertEquals("薄弱", result.get(0).getLevel());
        assertEquals("基本概念", result.get(1).getKnowledgePoint());
        assertEquals("掌握较好", result.get(1).getLevel());
    }

    @Test
    @DisplayName("同知识点题目合并统计")
    void testBuildKnowledgeMerge() {
        ExamAnalysisVO.QuestionStat a = new ExamAnalysisVO.QuestionStat();
        a.setKnowledgePoint("原理理解");
        a.setFullScore(new BigDecimal("10"));
        a.setAvgScore(new BigDecimal("6"));
        ExamAnalysisVO.QuestionStat b = new ExamAnalysisVO.QuestionStat();
        b.setKnowledgePoint("原理理解");
        b.setFullScore(new BigDecimal("10"));
        b.setAvgScore(new BigDecimal("4"));

        List<ExamAnalysisVO.KnowledgeMastery> result = service.buildKnowledge(List.of(a, b));
        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.get(0).getFullScore()));
        assertEquals(0, new BigDecimal("10.00").compareTo(result.get(0).getAvgScore()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.get(0).getScoreRate()));
    }

    @Test
    @DisplayName("无知识点题目归入未分类")
    void testBuildKnowledgeUncategorized() {
        ExamAnalysisVO.QuestionStat stat = new ExamAnalysisVO.QuestionStat();
        stat.setKnowledgePoint("");
        stat.setFullScore(new BigDecimal("10"));
        stat.setAvgScore(new BigDecimal("8"));
        assertEquals("未分类", service.buildKnowledge(List.of(stat)).get(0).getKnowledgePoint());
    }

    // ==================== 试卷分析 ====================

    @Test
    @DisplayName("分析计算得分率、难度与区分度")
    void testAnalyze() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        Mockito.when(paperMapper.selectById(1L)).thenReturn(paper);
        Mockito.when(questionMapper.selectList(any())).thenReturn(List.of(
                question(11L, "Q1", "基本概念", "10")));
        // 4 名学生，能力递减
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(
                answer(1L, 11L, 1L, "10"),
                answer(1L, 11L, 2L, "8"),
                answer(1L, 11L, 3L, "6"),
                answer(1L, 11L, 4L, "2")));

        ExamAnalysisVO vo = service.analyze(1L);
        assertEquals(1, vo.getQuestions().size());
        ExamAnalysisVO.QuestionStat stat = vo.getQuestions().get(0);
        assertEquals(0, new BigDecimal("6.50").compareTo(stat.getAvgScore()));
        assertEquals(0, new BigDecimal("65.00").compareTo(stat.getScoreRate()));
        assertEquals(0, new BigDecimal("0.650").compareTo(stat.getDifficulty()));
        // 高低分组各取 1 人：(10-2)/10 = 0.8
        assertEquals(0, new BigDecimal("0.800").compareTo(stat.getDiscrimination()));
        assertEquals(4, vo.getStudentCount());

        // 卷面统计
        Mockito.verify(paperMapper).updateById(paper);
        assertEquals(0, new BigDecimal("6.50").compareTo(paper.getAvgScore()));
        assertEquals(0, new BigDecimal("10").compareTo(paper.getMaxScore()));
        assertEquals(0, new BigDecimal("2").compareTo(paper.getMinScore()));
        assertEquals(0, new BigDecimal("0.650").compareTo(paper.getDifficulty()));
    }

    @Test
    @DisplayName("试卷不存在时抛异常")
    void testAnalyzeMissingPaper() {
        Mockito.when(paperMapper.selectById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.analyze(99L));
    }

    @Test
    @DisplayName("无题目或无答卷时返回空分析")
    void testAnalyzeEmpty() {
        ExamPaper paper = new ExamPaper();
        paper.setId(2L);
        Mockito.when(paperMapper.selectById(2L)).thenReturn(paper);
        Mockito.when(questionMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());

        ExamAnalysisVO vo = service.analyze(2L);
        assertTrue(vo.getQuestions().isEmpty());
        assertEquals(0, vo.getStudentCount());
    }

    @Test
    @DisplayName("满分为 0 时不产生除零异常")
    void testAnalyzeZeroFullScore() {
        ExamPaper paper = new ExamPaper();
        paper.setId(3L);
        Mockito.when(paperMapper.selectById(3L)).thenReturn(paper);
        Mockito.when(questionMapper.selectList(any())).thenReturn(List.of(
                question(31L, "Q1", "基本概念", "0")));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(
                answer(3L, 31L, 1L, "0")));

        ExamAnalysisVO vo = assertDoesNotThrow(() -> service.analyze(3L));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getQuestions().get(0).getScoreRate()));
    }

    // ==================== 演示数据 ====================

    @Test
    @DisplayName("生成演示试卷并产出题目与答卷")
    void testGenerateDemoPaper() {
        Score s1 = new Score();
        s1.setStudentId(1L);
        s1.setCourseOfferingId(10L);
        s1.setTotalScore(new BigDecimal("90"));
        Score s2 = new Score();
        s2.setStudentId(2L);
        s2.setCourseOfferingId(10L);
        s2.setTotalScore(new BigDecimal("50"));
        Mockito.when(courseScoreMapper.selectList(any())).thenReturn(List.of(s1, s2));
        // 模拟插入后回填自增主键
        Mockito.doAnswer(invocation -> {
            ExamPaper p = invocation.getArgument(0);
            p.setId(100L);
            return 1;
        }).when(paperMapper).insert(any(ExamPaper.class));

        Long paperId = service.generateDemoPaper(2L);
        assertNotNull(paperId);
        assertEquals(100L, paperId);
        Mockito.verify(paperMapper).insert(any(ExamPaper.class));
        // 10 道题
        Mockito.verify(questionMapper, Mockito.times(10)).insert(any(ExamQuestion.class));
        // 2 名学生 × 10 题
        Mockito.verify(scoreMapper, Mockito.times(20)).insert(any(ExamQuestionScore.class));
    }

    @Test
    @DisplayName("无成绩数据时生成演示试卷失败")
    void testGenerateDemoNoScore() {
        Mockito.when(courseScoreMapper.selectList(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.generateDemoPaper(2L));
    }

    @Test
    @DisplayName("演示数据得分不超满分也不为负")
    void testGenerateDemoScoreBounded() {
        Score s = new Score();
        s.setStudentId(1L);
        s.setCourseOfferingId(10L);
        s.setTotalScore(new BigDecimal("100"));
        Mockito.when(courseScoreMapper.selectList(any())).thenReturn(List.of(s));

        List<ExamQuestionScore> captured = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            captured.add(invocation.getArgument(0));
            return 1;
        }).when(scoreMapper).insert(any(ExamQuestionScore.class));

        service.generateDemoPaper(2L);
        assertEquals(10, captured.size());
        captured.forEach(c -> {
            assertTrue(c.getScore().compareTo(BigDecimal.ZERO) >= 0, "得分不应为负");
            assertTrue(c.getScore().compareTo(new BigDecimal("20")) <= 0, "得分不应超过满分 20");
        });
    }

    @Test
    @DisplayName("查询分析只读不回写")
    void testGetAnalysisNoWrite() {
        ExamPaper paper = new ExamPaper();
        paper.setId(4L);
        paper.setAvgScore(new BigDecimal("70"));
        Mockito.when(paperMapper.selectById(4L)).thenReturn(paper);
        Mockito.when(questionMapper.selectList(any())).thenReturn(List.of(
                question(41L, "Q1", "基本概念", "10")));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(answer(4L, 41L, 1L, "7")));

        ExamAnalysisVO vo = service.getAnalysis(4L);
        assertEquals(1, vo.getQuestions().size());
        Mockito.verify(paperMapper, Mockito.never()).updateById(any(ExamPaper.class));
    }
}
