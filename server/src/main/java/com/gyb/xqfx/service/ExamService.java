package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.ExamPaper;
import com.gyb.xqfx.entity.ExamQuestion;
import com.gyb.xqfx.entity.ExamQuestionScore;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.mapper.ExamPaperMapper;
import com.gyb.xqfx.mapper.ExamQuestionMapper;
import com.gyb.xqfx.mapper.ExamQuestionScoreMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.model.vo.ExamAnalysisVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 试卷与知识点分析服务
 *
 * <p>基于教育测量学方法计算：平均得分、得分率、难度系数、区分度（高低分组法）、
 * 标准差，并按知识点汇总掌握度，用于定位教与学的薄弱环节。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    /** 高低分组比例：教育测量学常用 27% */
    private static final double GROUP_RATIO = 0.27;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamQuestionScoreMapper scoreMapper;
    private final ScoreMapper courseScoreMapper;

    /**
     * 分析试卷：计算各题统计量并回写题目与试卷表
     *
     * @return 分析结果
     */
    public ExamAnalysisVO analyze(Long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在：" + paperId);
        }
        List<ExamQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, paperId));
        List<ExamQuestionScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionScore>().eq(ExamQuestionScore::getExamPaperId, paperId));
        if (questions.isEmpty() || scores.isEmpty()) {
            return buildEmptyAnalysis(paper);
        }

        // 按学生汇总总分，用于高低分组与卷面统计
        Map<Long, BigDecimal> studentTotal = new LinkedHashMap<>();
        Map<Long, Map<Long, BigDecimal>> byStudent = new LinkedHashMap<>();
        for (ExamQuestionScore s : scores) {
            if (s.getScore() == null) {
                continue;
            }
            studentTotal.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
            byStudent.computeIfAbsent(s.getStudentId(), k -> new LinkedHashMap<>())
                    .put(s.getExamQuestionId(), s.getScore());
        }
        List<Long> highGroup = selectGroup(studentTotal, true);
        List<Long> lowGroup = selectGroup(studentTotal, false);

        List<ExamAnalysisVO.QuestionStat> stats = new ArrayList<>();
        BigDecimal discriminationSum = BigDecimal.ZERO;
        BigDecimal difficultySum = BigDecimal.ZERO;

        for (ExamQuestion q : questions) {
            List<BigDecimal> values = new ArrayList<>();
            for (ExamQuestionScore s : scores) {
                if (q.getId().equals(s.getExamQuestionId()) && s.getScore() != null) {
                    values.add(s.getScore());
                }
            }
            ExamAnalysisVO.QuestionStat stat = new ExamAnalysisVO.QuestionStat();
            stat.setQuestionId(q.getId());
            stat.setQuestionNo(q.getQuestionNo());
            stat.setQuestionType(q.getQuestionType());
            stat.setKnowledgePoint(q.getKnowledgePoint());
            stat.setFullScore(q.getFullScore());

            BigDecimal full = q.getFullScore() == null ? BigDecimal.ZERO : q.getFullScore();
            BigDecimal avg = mean(values);
            stat.setAvgScore(avg);
            stat.setScoreRate(full.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO : rate(avg, full));
            stat.setStdDev(stdDev(values));
            BigDecimal difficulty = full.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO : avg.divide(full, 3, RoundingMode.HALF_UP);
            stat.setDifficulty(difficulty);
            BigDecimal discrimination = discrimination(q.getId(), full, byStudent, highGroup, lowGroup);
            stat.setDiscrimination(discrimination);
            stats.add(stat);

            discriminationSum = discriminationSum.add(discrimination);
            difficultySum = difficultySum.add(difficulty);

            q.setAvgScore(avg);
            q.setScoreRate(stat.getScoreRate());
            q.setDifficulty(difficulty);
            q.setDiscrimination(discrimination);
            q.setStdDev(stat.getStdDev());
            questionMapper.updateById(q);
        }

        // 卷面统计
        List<BigDecimal> totals = new ArrayList<>(studentTotal.values());
        BigDecimal avgTotal = mean(totals);
        BigDecimal max = totals.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal min = totals.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal fullTotal = questions.stream()
                .map(ExamQuestion::getFullScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long passCount = totals.stream().filter(t -> t.compareTo(fullTotal.multiply(new BigDecimal("0.6"))) >= 0).count();
        long excellentCount = totals.stream()
                .filter(t -> t.compareTo(fullTotal.multiply(new BigDecimal("0.9"))) >= 0).count();

        paper.setTotalScore(fullTotal);
        paper.setAvgScore(avgTotal);
        paper.setMaxScore(max);
        paper.setMinScore(min);
        paper.setStdDev(stdDev(totals));
        paper.setPassRate(totals.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(passCount * 100L).divide(BigDecimal.valueOf(totals.size()), 2, RoundingMode.HALF_UP));
        paper.setExcellentRate(totals.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(excellentCount * 100L).divide(BigDecimal.valueOf(totals.size()), 2, RoundingMode.HALF_UP));
        paper.setDifficulty(questions.isEmpty() ? BigDecimal.ZERO
                : difficultySum.divide(BigDecimal.valueOf(questions.size()), 3, RoundingMode.HALF_UP));
        paper.setDiscrimination(questions.isEmpty() ? BigDecimal.ZERO
                : discriminationSum.divide(BigDecimal.valueOf(questions.size()), 3, RoundingMode.HALF_UP));
        paperMapper.updateById(paper);

        return buildAnalysis(paper, stats, totals);
    }

    /**
     * 获取试卷分析结果（不重新计算，适合只读展示）
     */
    public ExamAnalysisVO getAnalysis(Long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在：" + paperId);
        }
        List<ExamQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, paperId));
        List<ExamAnalysisVO.QuestionStat> stats = new ArrayList<>();
        for (ExamQuestion q : questions) {
            ExamAnalysisVO.QuestionStat stat = new ExamAnalysisVO.QuestionStat();
            stat.setQuestionId(q.getId());
            stat.setQuestionNo(q.getQuestionNo());
            stat.setQuestionType(q.getQuestionType());
            stat.setKnowledgePoint(q.getKnowledgePoint());
            stat.setFullScore(q.getFullScore());
            stat.setAvgScore(q.getAvgScore());
            stat.setScoreRate(q.getScoreRate());
            stat.setDifficulty(q.getDifficulty());
            stat.setDiscrimination(q.getDiscrimination());
            stat.setStdDev(q.getStdDev());
            stats.add(stat);
        }
        List<ExamQuestionScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionScore>().eq(ExamQuestionScore::getExamPaperId, paperId));
        Map<Long, BigDecimal> studentTotal = new LinkedHashMap<>();
        for (ExamQuestionScore s : scores) {
            if (s.getScore() != null) {
                studentTotal.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
            }
        }
        return buildAnalysis(paper, stats, new ArrayList<>(studentTotal.values()));
    }

    /**
     * 生成演示试卷数据：依据学生已有成绩水平生成自洽的小题得分
     *
     * @return 试卷ID
     */
    public Long generateDemoPaper(Long termId) {
        List<Score> courseScores = courseScoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        if (courseScores.isEmpty()) {
            throw new IllegalArgumentException("该学期暂无成绩数据，无法生成试卷");
        }
        Long offeringId = courseScores.get(0).getCourseOfferingId();
        List<Score> sameCourse = courseScores.stream()
                .filter(s -> offeringId != null && offeringId.equals(s.getCourseOfferingId()))
                .toList();
        if (sameCourse.isEmpty()) {
            sameCourse = courseScores.subList(0, Math.min(courseScores.size(), 40));
        }

        ExamPaper paper = new ExamPaper();
        paper.setCourseOfferingId(offeringId);
        paper.setTermId(termId);
        paper.setPaperName("期末考核试卷（演示）");
        paperMapper.insert(paper);

        String[] knowledgePoints = {"基本概念", "原理理解", "综合应用", "计算分析", "案例设计"};
        String[] types = {"SINGLE", "MULTI", "JUDGE", "FILL", "SHORT"};
        BigDecimal[] fullScores = {new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("20")};
        Random random = new Random(termId == null ? 1 : termId);

        List<ExamQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ExamQuestion q = new ExamQuestion();
            q.setExamPaperId(paper.getId());
            q.setQuestionNo("Q" + i);
            String type = types[(i - 1) % types.length];
            String point = knowledgePoints[(i - 1) % knowledgePoints.length];
            q.setQuestionType(type);
            q.setKnowledgePoint(point);
            // 补出题内容：本地大模型要靠题干、选项与答案生成同类题
            q.setStem(buildStem(type, point, i));
            q.setOptionsJson(buildOptions(type, point));
            q.setAnswer(buildAnswer(type));
            q.setAnalysis(buildAnalysis(type, point));
            BigDecimal full = fullScores[(i - 1) % fullScores.length];
            q.setFullScore(full);
            questionMapper.insert(q);
            questions.add(q);
        }

        // 题目难度：0.40 ~ 0.90，围绕中等难度分布
        double[] difficulty = new double[questions.size()];
        for (int i = 0; i < difficulty.length; i++) {
            difficulty[i] = 0.40 + random.nextDouble() * 0.5;
        }

        List<ExamQuestionScore> toInsert = new ArrayList<>();
        for (Score sc : sameCourse) {
            double ability = sc.getTotalScore().doubleValue() / 100.0;
            for (int i = 0; i < questions.size(); i++) {
                ExamQuestion q = questions.get(i);
                double full = q.getFullScore().doubleValue();
                // 得分率以学生能力为主，按题目难度微调并叠加小幅噪声，
                // 使卷面总分分布与学生真实成绩水平一致
                double rate = ability + (difficulty[i] - 0.65) * 0.5
                        + (random.nextDouble() - 0.5) * 0.12;
                rate = Math.max(0, Math.min(1, rate));
                double value = full * rate;
                ExamQuestionScore qs = new ExamQuestionScore();
                qs.setExamPaperId(paper.getId());
                qs.setExamQuestionId(q.getId());
                qs.setStudentId(sc.getStudentId());
                qs.setScore(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP));
                qs.setCreateTime(java.time.LocalDateTime.now());
                qs.setUpdateTime(java.time.LocalDateTime.now());
                qs.setIsDeleted(0);
                toInsert.add(qs);
            }
        }
        toInsert.forEach(scoreMapper::insert);
        log.info("生成演示试卷：paperId={} 题目数={} 答卷数={}", paper.getId(), questions.size(), sameCourse.size());
        return paper.getId();
    }

    /**
     * 演示题目题干：真实试卷应由教师录入，此处按题型与知识点合成示例内容
     */
    private String buildStem(String type, String point, int index) {
        return switch (type) {
            case "SINGLE" -> "第 " + index + " 题（单选）下列关于「" + point + "」的表述，正确的一项是？";
            case "MULTI" -> "第 " + index + " 题（多选）下列关于「" + point + "」的表述，正确的有多项？";
            case "JUDGE" -> "第 " + index + " 题（判断）「" + point + "」的适用条件与教材定义一致，请判断正误。";
            case "FILL" -> "第 " + index + " 题（填空）「" + point + "」的核心思想是______，其关键在于______。";
            default -> "第 " + index + " 题（简答）简述「" + point + "」的基本含义，并结合实例说明其应用。";
        };
    }

    /**
     * 演示题目选项（JSON 数组），仅选择题有
     */
    private String buildOptions(String type, String point) {
        if (!"SINGLE".equals(type) && !"MULTI".equals(type)) {
            return null;
        }
        return "[\"A. 准确表述「" + point + "」的定义与适用条件\","
                + "\"B. 将「" + point + "」与相近概念混淆\","
                + "\"C. 忽略「" + point + "」成立的前提假设\","
                + "\"D. 颠倒「" + point + "」的因果关系\"]";
    }

    private String buildAnswer(String type) {
        return switch (type) {
            case "SINGLE" -> "A";
            case "MULTI" -> "AB";
            case "JUDGE" -> "正确";
            default -> "见解析要点";
        };
    }

    private String buildAnalysis(String type, String point) {
        return "本题考查「" + point + "」。解题关键在于明确其定义、成立条件与常见易错点，"
                + "注意与相近概念区分，避免忽略前提假设或颠倒因果关系。";
    }

    // ==================== 内部方法 ====================

    /** 按总分选取高分组或低分组（各 27%，至少 1 人） */
    private List<Long> selectGroup(Map<Long, BigDecimal> studentTotal, boolean high) {
        List<Map.Entry<Long, BigDecimal>> sorted = new ArrayList<>(studentTotal.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        int size = Math.max(1, (int) Math.round(sorted.size() * GROUP_RATIO));
        size = Math.min(size, sorted.size());
        if (high) {
            return sorted.subList(0, size).stream().map(Map.Entry::getKey).toList();
        }
        return sorted.subList(sorted.size() - size, sorted.size()).stream().map(Map.Entry::getKey).toList();
    }

    /** 区分度 = (高分组平均分 - 低分组平均分) / 满分 */
    private BigDecimal discrimination(Long questionId, BigDecimal full,
                                      Map<Long, Map<Long, BigDecimal>> byStudent,
                                      List<Long> highGroup, List<Long> lowGroup) {
        if (full.compareTo(BigDecimal.ZERO) == 0 || highGroup.isEmpty() || lowGroup.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal highAvg = mean(highGroup.stream()
                .map(id -> byStudent.getOrDefault(id, Map.of()).get(questionId))
                .filter(Objects::nonNull).toList());
        BigDecimal lowAvg = mean(lowGroup.stream()
                .map(id -> byStudent.getOrDefault(id, Map.of()).get(questionId))
                .filter(Objects::nonNull).toList());
        return highAvg.subtract(lowAvg).divide(full, 3, RoundingMode.HALF_UP);
    }

    private ExamAnalysisVO buildAnalysis(ExamPaper paper, List<ExamAnalysisVO.QuestionStat> stats,
                                         List<BigDecimal> totals) {
        ExamAnalysisVO vo = new ExamAnalysisVO();
        vo.setPaper(paper);
        vo.setQuestions(stats);
        vo.setStudentCount(totals.size());
        vo.setKnowledge(buildKnowledge(stats));
        vo.setDistribution(buildDistribution(totals, paper.getTotalScore()));
        return vo;
    }

    private ExamAnalysisVO buildEmptyAnalysis(ExamPaper paper) {
        ExamAnalysisVO vo = new ExamAnalysisVO();
        vo.setPaper(paper);
        vo.setQuestions(List.of());
        vo.setKnowledge(List.of());
        vo.setDistribution(List.of());
        vo.setStudentCount(0);
        return vo;
    }

    /** 按知识点汇总掌握度 */
    public List<ExamAnalysisVO.KnowledgeMastery> buildKnowledge(List<ExamAnalysisVO.QuestionStat> stats) {
        Map<String, List<ExamAnalysisVO.QuestionStat>> grouped = new LinkedHashMap<>();
        for (ExamAnalysisVO.QuestionStat s : stats) {
            String key = s.getKnowledgePoint() == null || s.getKnowledgePoint().isBlank()
                    ? "未分类" : s.getKnowledgePoint();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<ExamAnalysisVO.KnowledgeMastery> result = new ArrayList<>();
        grouped.forEach((point, list) -> {
            BigDecimal full = list.stream().map(ExamAnalysisVO.QuestionStat::getFullScore)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = list.stream().map(ExamAnalysisVO.QuestionStat::getAvgScore)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            ExamAnalysisVO.KnowledgeMastery m = new ExamAnalysisVO.KnowledgeMastery();
            m.setKnowledgePoint(point);
            m.setFullScore(full.setScale(2, RoundingMode.HALF_UP));
            m.setAvgScore(avg.setScale(2, RoundingMode.HALF_UP));
            BigDecimal scoreRate = full.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : rate(avg, full);
            m.setScoreRate(scoreRate);
            m.setLevel(evalMastery(scoreRate));
            result.add(m);
        });
        result.sort(Comparator.comparing(ExamAnalysisVO.KnowledgeMastery::getScoreRate));
        return result;
    }

    /** 掌握度评价：>=85 掌握较好，>=60 一般，否则薄弱 */
    public String evalMastery(BigDecimal scoreRate) {
        if (scoreRate == null) {
            return "薄弱";
        }
        if (scoreRate.compareTo(new BigDecimal("85")) >= 0) {
            return "掌握较好";
        }
        if (scoreRate.compareTo(new BigDecimal("60")) >= 0) {
            return "一般";
        }
        return "薄弱";
    }

    /** 学生总分分布：按满分比例分 5 段 */
    private List<ExamAnalysisVO.NameValue> buildDistribution(List<BigDecimal> totals, BigDecimal full) {
        if (totals.isEmpty()) {
            return List.of();
        }
        BigDecimal base = (full == null || full.compareTo(BigDecimal.ZERO) == 0)
                ? new BigDecimal("100") : full;
        int[] buckets = new int[5];
        for (BigDecimal t : totals) {
            double r = t.divide(base, 4, RoundingMode.HALF_UP).doubleValue();
            int idx = r < 0.6 ? 0 : r < 0.7 ? 1 : r < 0.8 ? 2 : r < 0.9 ? 3 : 4;
            buckets[idx]++;
        }
        String[] names = {"60%以下", "60-69%", "70-79%", "80-89%", "90%及以上"};
        List<ExamAnalysisVO.NameValue> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            list.add(new ExamAnalysisVO.NameValue(names[i], buckets[i]));
        }
        return list;
    }

    /** 百分比 */
    private BigDecimal rate(BigDecimal value, BigDecimal full) {
        if (full == null || full.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(HUNDRED).divide(full, 2, RoundingMode.HALF_UP);
    }

    /** 均值 */
    public BigDecimal mean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /** 总体标准差 */
    public BigDecimal stdDev(List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        double sumSq = values.stream()
                .mapToDouble(v -> v.subtract(mean).doubleValue())
                .map(d -> d * d).sum();
        return BigDecimal.valueOf(Math.sqrt(sumSq / values.size())).setScale(3, RoundingMode.HALF_UP);
    }
}
