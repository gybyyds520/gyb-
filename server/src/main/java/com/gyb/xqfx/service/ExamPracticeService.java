package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.AiCallLog;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 错题举一反三：用本地大模型围绕学生做错的题目生成同类巩固练习
 *
 * <p>错题判定：{@code exam_question_score.score < exam_question.full_score}（学生维度），
 * 或题目得分率低于 {@link #WEAK_SCORE_RATE}（班级维度薄弱题）。</p>
 *
 * <p>生成链路：原题信息 + 知识点 → 本地 Ollama 模型 → 严格 JSON 数组 → 解析落库。
 * 模型不可用或输出无法解析时，降级为基于知识点的规则化复习任务，
 * 并在 {@code source_type} 标注 RULE，不使用云端模型兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamPracticeService {

    /** 班级维度薄弱题阈值：得分率低于该值视为需要巩固 */
    private static final BigDecimal WEAK_SCORE_RATE = new BigDecimal("60");

    /** 单次生成题数上限，避免本地模型输出过长 */
    private static final int MAX_COUNT = 6;

    private static final String SYSTEM_PROMPT =
            "你是高校课程的资深助教，擅长命题与学情诊断。"
            + "你只输出严格的 JSON，不要输出任何解释、Markdown 代码块或多余文字。";

    private final ExamPracticeMapper practiceMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final ExamQuestionScoreMapper questionScoreMapper;
    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseMapper courseMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    @Value("${xqfx.ollama.enabled:true}")
    private boolean llmEnabled;

    /** 练习题生成专用模型：命题对生成质量要求更高，可单独指定规模更大的本地模型 */
    @Value("${xqfx.ollama.practice-model:}")
    private String practiceModel;

    // ==================== 错题查询 ====================

    /**
     * 班级维度薄弱题（得分率低于阈值），按得分率升序
     */
    public List<WrongQuestion> weakQuestionsOfPaper(Long paperId) {
        List<ExamQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, paperId));
        List<WrongQuestion> list = new ArrayList<>();
        for (ExamQuestion q : questions) {
            BigDecimal rate = q.getScoreRate() == null ? BigDecimal.ZERO : q.getScoreRate();
            if (rate.compareTo(WEAK_SCORE_RATE) >= 0) {
                continue;
            }
            list.add(toWrong(q, null, countWrong(paperId, q.getId())));
        }
        list.sort((a, b) -> nz(a.getScoreRate()).compareTo(nz(b.getScoreRate())));
        return list;
    }

    /**
     * 学生维度错题：该生在这份试卷上未得满分的题目
     */
    public List<WrongQuestion> wrongQuestionsOfStudent(Long paperId, Long studentId) {
        List<ExamQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, paperId));
        List<ExamQuestionScore> scores = questionScoreMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionScore>()
                        .eq(ExamQuestionScore::getExamPaperId, paperId)
                        .eq(ExamQuestionScore::getStudentId, studentId));
        List<WrongQuestion> list = new ArrayList<>();
        for (ExamQuestion q : questions) {
            BigDecimal got = scores.stream()
                    .filter(s -> q.getId().equals(s.getExamQuestionId()))
                    .map(ExamQuestionScore::getScore)
                    .findFirst().orElse(null);
            BigDecimal full = nz(q.getFullScore());
            if (got != null && got.compareTo(full) >= 0) {
                continue;
            }
            WrongQuestion w = toWrong(q, got, null);
            w.setLostScore(full.subtract(nz(got)));
            w.setIsWrong(got != null);
            list.add(w);
        }
        list.sort((a, b) -> nz(b.getLostScore()).compareTo(nz(a.getLostScore())));
        return list;
    }

    private Long countWrong(Long paperId, Long questionId) {
        List<ExamQuestionScore> scores = questionScoreMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionScore>()
                        .eq(ExamQuestionScore::getExamPaperId, paperId)
                        .eq(ExamQuestionScore::getExamQuestionId, questionId));
        ExamQuestion q = questionMapper.selectById(questionId);
        BigDecimal full = q == null ? BigDecimal.ZERO : nz(q.getFullScore());
        return scores.stream().filter(s -> nz(s.getScore()).compareTo(full) < 0).count();
    }

    private WrongQuestion toWrong(ExamQuestion q, BigDecimal studentScore, Long wrongCount) {
        WrongQuestion w = new WrongQuestion();
        w.setQuestionId(q.getId());
        w.setQuestionNo(q.getQuestionNo());
        w.setQuestionType(q.getQuestionType());
        w.setKnowledgePoint(q.getKnowledgePoint());
        w.setFullScore(q.getFullScore());
        w.setAvgScore(q.getAvgScore());
        w.setScoreRate(q.getScoreRate());
        w.setStem(q.getStem());
        w.setStudentScore(studentScore);
        w.setWrongCount(wrongCount);
        return w;
    }

    // ==================== 举一反三生成 ====================

    /**
     * 为错题生成同类巩固练习
     *
     * @param paperId    试卷ID
     * @param questionId 错题（题目）ID
     * @param studentId  目标学生，为空表示面向全班
     * @param count      生成题数（1~6）
     */
    @Transactional(rollbackFor = Exception.class)
    public PracticeResult generate(Long paperId, Long questionId, Long studentId, Integer count) {
        ExamQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        if (paperId == null) {
            paperId = question.getExamPaperId();
        }
        int size = count == null ? 3 : Math.max(1, Math.min(MAX_COUNT, count));

        String courseName = courseNameOf(paperId);
        BigDecimal studentScore = studentId == null ? null : scoreOf(paperId, questionId, studentId);

        long start = System.currentTimeMillis();
        String model = null;
        String sourceType = "AI";
        String status = "SUCCESS";
        String errorMsg = null;
        int promptTokens = 0;
        int completionTokens = 0;

        List<PracticeQuestion> items = null;
        if (llmEnabled) {
            String prompt = buildPrompt(question, courseName, studentScore, size);
            OllamaClient.ChatResult chat = ollamaClient.chatWithModel(practiceModel, SYSTEM_PROMPT, prompt);
            if (chat != null && chat.content != null) {
                items = parseQuestions(chat.content);
                model = chat.model;
                promptTokens = chat.promptTokens;
                completionTokens = chat.completionTokens;
                if (items == null || items.isEmpty()) {
                    status = "FALLBACK";
                    errorMsg = "模型输出无法解析为练习题";
                }
            } else {
                status = "FALLBACK";
                errorMsg = "本地模型不可用";
            }
        } else {
            status = "FALLBACK";
            errorMsg = "本地模型未启用";
            sourceType = "RULE";
        }

        if (items == null || items.isEmpty()) {
            items = buildRuleBased(question, courseName, size);
            sourceType = "RULE";
        }

        int costMs = (int) (System.currentTimeMillis() - start);

        ExamPractice practice = new ExamPractice();
        practice.setExamPaperId(paperId);
        practice.setExamQuestionId(questionId);
        practice.setStudentId(studentId);
        practice.setCourseName(courseName);
        practice.setKnowledgePoint(question.getKnowledgePoint());
        practice.setQuestionType(question.getQuestionType());
        practice.setSourceType(sourceType);
        practice.setModelName(model);
        practice.setQuestionCount(items.size());
        practice.setContentJson(toJson(items));
        practice.setPromptTokens(promptTokens);
        practice.setCompletionTokens(completionTokens);
        practice.setCostMs(costMs);
        practice.setStatus(status);
        practice.setErrorMsg(errorMsg);
        practice.setUserId(com.gyb.xqfx.security.SecurityUtils.currentUserIdOrNull());
        practice.setCreateTime(LocalDateTime.now());
        practice.setUpdateTime(LocalDateTime.now());
        practice.setIsDeleted(0);
        practiceMapper.insert(practice);

        logCall(practice.getId(), model, promptTokens, completionTokens, costMs, status, errorMsg);

        PracticeResult result = new PracticeResult();
        result.setPracticeId(practice.getId());
        result.setQuestionId(questionId);
        result.setStudentId(studentId);
        result.setCourseName(courseName);
        result.setKnowledgePoint(question.getKnowledgePoint());
        result.setQuestionType(question.getQuestionType());
        result.setSourceType(sourceType);
        result.setModel(model);
        result.setStatus(status);
        result.setErrorMsg(errorMsg);
        result.setCostMs(costMs);
        result.setItems(items);
        return result;
    }

    /**
     * 构造举一反三提示词：以原题题干、选项、答案与知识点为依据
     */
    public String buildPrompt(ExamQuestion q, String courseName, BigDecimal studentScore, int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生在《").append(courseName == null ? "本课程" : courseName)
                .append("》考试中做错了下面这道题，请围绕同一知识点生成 ").append(size)
                .append(" 道同类练习题，用于举一反三、巩固薄弱点。\n\n");
        sb.append("【原题信息】\n");
        sb.append("- 知识点：").append(nvl(q.getKnowledgePoint())).append("\n");
        sb.append("- 题型：").append(typeName(q.getQuestionType())).append("\n");
        sb.append("- 题干：").append(nvl(q.getStem())).append("\n");
        sb.append("- 选项：").append(optionsText(q.getOptionsJson())).append("\n");
        sb.append("- 参考答案：").append(nvl(q.getAnswer())).append("\n");
        sb.append("- 满分：").append(nz(q.getFullScore()));
        sb.append("，该生得分：").append(studentScore == null ? "未记录" : studentScore).append("\n");
        sb.append("- 全班得分率：").append(q.getScoreRate() == null ? "未知" : q.getScoreRate()).append("%\n\n");
        sb.append("【生成要求】\n");
        sb.append("1. 与原题考查同一知识点，难度相当或略低，便于巩固；\n");
        sb.append("2. 题目情境、数据与表述必须与原题不同，不得重复原题；\n");
        sb.append("3. 难度由易到难递进，第 1 道最简单；\n");
        sb.append("4. 每道题给出正确答案与简要解析，解析要点明考查的概念与易错点；\n");
        sb.append("5. 选择题给出 4 个选项，格式如 \"A. ...\"；非选择题 options 传空数组；\n");
        sb.append("6. 所有内容使用中文。\n\n");
        // 小参数模型输出 JSON 时常出现括号错位，改用标记式文本，解析更鲁棒
        sb.append("【输出格式】每道题之间用单独一行 ==== 分隔，严格按下列标记输出，"
                + "不要输出 JSON、代码块或任何多余说明：\n");
        sb.append("====\n");
        sb.append("【题干】在这里写完整题干\n");
        sb.append("【选项】A. 选项一；B. 选项二；C. 选项三；D. 选项四（非选择题写“无”）\n");
        sb.append("【答案】B\n");
        sb.append("【解析】写明考查的概念、解题关键与常见易错点\n");
        sb.append("【难度】EASY\n");
        sb.append("====\n");
        sb.append("【题干】第二道题...\n");
        return sb.toString();
    }

    /**
     * 解析模型输出的练习题 JSON 数组，无法解析返回 null
     */
    public List<PracticeQuestion> parseQuestions(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        // 双通道：先按 JSON 解析（模型遵守 JSON 要求时），失败再按标记式文本解析
        List<PracticeQuestion> items = parseJson(content);
        if (items != null && !items.isEmpty()) {
            return items;
        }
        items = parseMarked(content);
        return (items == null || items.isEmpty()) ? null : items;
    }

    /**
     * 按 JSON 数组解析模型输出
     */
    private List<PracticeQuestion> parseJson(String content) {
        try {
            String text = content.trim();
            // 去掉 ```json ... ``` 围栏
            text = text.replaceAll("(?s)^```(?:json)?", "").replaceAll("(?s)```$", "").trim();
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end <= start) {
                log.warn("模型输出中未找到 JSON 数组");
                return null;
            }
            JsonNode arr = objectMapper.readTree(text.substring(start, end + 1));
            if (arr == null || !arr.isArray()) {
                return null;
            }
            List<PracticeQuestion> items = new ArrayList<>();
            int idx = 1;
            for (JsonNode node : arr) {
                String stem = node.path("stem").asText(null);
                if (stem == null || stem.isBlank()) {
                    continue;
                }
                PracticeQuestion item = new PracticeQuestion();
                item.setIndex(idx++);
                item.setStem(stem.trim());
                item.setOptions(readOptions(node.path("options")));
                item.setAnswer(node.path("answer").asText(""));
                item.setAnalysis(node.path("analysis").asText(""));
                item.setDifficulty(node.path("difficulty").asText("MEDIUM"));
                items.add(item);
            }
            return items.isEmpty() ? null : items;
        } catch (Exception e) {
            log.warn("解析模型输出失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 按标记式文本解析模型输出（【题干】/【选项】/【答案】/【解析】/【难度】）
     *
     * <p>小参数模型输出 JSON 时容易出现括号错位，标记式格式对格式瑕疵容忍度更高，
     * 只要字段名出现即可提取内容。</p>
     */
    private List<PracticeQuestion> parseMarked(String content) {
        if (content == null || !content.contains("【题干】")) {
            return null;
        }
        String[] blocks = content.split("(?m)^\\s*={3,}\\s*$");
        List<PracticeQuestion> items = new ArrayList<>();
        int idx = 1;
        for (String block : blocks) {
            String stem = pick(block, "题干");
            if (stem == null || stem.isBlank()) {
                continue;
            }
            PracticeQuestion item = new PracticeQuestion();
            item.setIndex(idx++);
            item.setStem(stem);
            item.setOptions(splitOptions(pick(block, "选项")));
            item.setAnswer(pick(block, "答案") == null ? "" : pick(block, "答案"));
            item.setAnalysis(pick(block, "解析") == null ? "" : pick(block, "解析"));
            item.setDifficulty(normalizeDifficulty(pick(block, "难度")));
            items.add(item);
        }
        return items.isEmpty() ? null : items;
    }

    /** 提取【字段名】到下一个【标记】之间的内容 */
    private String pick(String block, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("【" + field + "】([\\s\\S]*?)(?=【|$)")
                .matcher(block);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replace("\r", "").trim();
    }

    /** 选项按中文分号、英文分号或换行切分；"无"表示非选择题 */
    private List<String> splitOptions(String raw) {
        if (raw == null || raw.isBlank() || "无".equals(raw.trim())) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (String part : raw.split("[；;\\n]+")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                options.add(v);
            }
        }
        return options;
    }

    /** 难度归一化为 EASY / MEDIUM / HARD */
    private String normalizeDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MEDIUM";
        }
        String v = raw.toUpperCase();
        if (v.contains("EASY") || v.contains("简单") || v.contains("基础") || v.contains("易")) {
            return "EASY";
        }
        if (v.contains("HARD") || v.contains("困难") || v.contains("提高") || v.contains("难")) {
            return "HARD";
        }
        return "MEDIUM";
    }

    private List<String> readOptions(JsonNode node) {
        List<String> options = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode o : node) {
                String v = o.asText(null);
                if (v != null && !v.isBlank()) {
                    options.add(v.trim());
                }
            }
        }
        return options;
    }

    /**
     * 规则降级：本地模型不可用时，给出围绕该知识点的复习任务，明确标注来源
     */
    public List<PracticeQuestion> buildRuleBased(ExamQuestion q, String courseName, int size) {
        String kp = nvl(q.getKnowledgePoint());
        String type = typeName(q.getQuestionType());
        List<PracticeQuestion> items = new ArrayList<>();
        String[] levels = {"EASY", "MEDIUM", "HARD"};
        for (int i = 0; i < size; i++) {
            PracticeQuestion item = new PracticeQuestion();
            item.setIndex(i + 1);
            item.setStem("（本地模型暂不可用，以下为规则生成的复习任务）"
                    + "请围绕《" + (courseName == null ? "本课程" : courseName) + "》的知识点「" + kp
                    + "」完成 1 道" + type + "练习：复习教材对应章节的定义与例题，"
                    + "再独立作答课后同类型第 " + (i + 1) + " 题，并对照答案订正。");
            item.setOptions(List.of());
            item.setAnswer("见教材参考答案");
            item.setAnalysis("重点理解「" + kp + "」的概念、适用条件与常见易错点；"
                    + "订正时写明自己错在哪一步，避免重复出错。");
            item.setDifficulty(levels[Math.min(i, levels.length - 1)]);
            items.add(item);
        }
        return items;
    }

    // ==================== 查询与详情 ====================

    /**
     * 练习详情
     */
    public PracticeResult detail(Long practiceId) {
        ExamPractice p = practiceMapper.selectById(practiceId);
        if (p == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "练习记录不存在");
        }
        return toResult(p);
    }

    /**
     * 学生的巩固练习列表
     *
     * <p>仅包含两类：明确指派给本人的练习，以及本人参加过考试的试卷上的全班练习
     * （{@code student_id} 为空表示面向全班）。不返回其他班级或其他学生的练习。</p>
     */
    public List<PracticeBrief> myPractices(Long studentId) {
        // 本人参加过考试的试卷
        List<Long> paperIds = questionScoreMapper.selectList(
                        new LambdaQueryWrapper<ExamQuestionScore>()
                                .eq(ExamQuestionScore::getStudentId, studentId))
                .stream().map(ExamQuestionScore::getExamPaperId)
                .filter(java.util.Objects::nonNull).distinct().toList();

        LambdaQueryWrapper<ExamPractice> wrapper = new LambdaQueryWrapper<ExamPractice>()
                .eq(ExamPractice::getStudentId, studentId);
        if (!paperIds.isEmpty()) {
            wrapper.or(q -> q.isNull(ExamPractice::getStudentId)
                    .in(ExamPractice::getExamPaperId, paperIds));
        }
        wrapper.orderByDesc(ExamPractice::getCreateTime);

        List<PracticeBrief> briefs = new ArrayList<>();
        for (ExamPractice p : practiceMapper.selectList(wrapper)) {
            briefs.add(toBrief(p));
        }
        return briefs;
    }

    /**
     * 某道错题的历史生成记录（教师端）
     */
    public List<PracticeBrief> practicesOfQuestion(Long questionId) {
        List<ExamPractice> list = practiceMapper.selectList(new LambdaQueryWrapper<ExamPractice>()
                .eq(ExamPractice::getExamQuestionId, questionId)
                .orderByDesc(ExamPractice::getCreateTime));
        List<PracticeBrief> briefs = new ArrayList<>();
        for (ExamPractice p : list) {
            briefs.add(toBrief(p));
        }
        return briefs;
    }

    private PracticeResult toResult(ExamPractice p) {
        PracticeResult r = new PracticeResult();
        r.setPracticeId(p.getId());
        r.setQuestionId(p.getExamQuestionId());
        r.setStudentId(p.getStudentId());
        r.setCourseName(p.getCourseName());
        r.setKnowledgePoint(p.getKnowledgePoint());
        r.setQuestionType(p.getQuestionType());
        r.setSourceType(p.getSourceType());
        r.setModel(p.getModelName());
        r.setStatus(p.getStatus());
        r.setErrorMsg(p.getErrorMsg());
        r.setCostMs(p.getCostMs());
        r.setCreateTime(p.getCreateTime());
        r.setItems(parseQuestions(p.getContentJson()));
        return r;
    }

    private PracticeBrief toBrief(ExamPractice p) {
        PracticeBrief b = new PracticeBrief();
        b.setPracticeId(p.getId());
        b.setQuestionId(p.getExamQuestionId());
        b.setCourseName(p.getCourseName());
        b.setKnowledgePoint(p.getKnowledgePoint());
        b.setQuestionType(p.getQuestionType());
        b.setSourceType(p.getSourceType());
        b.setModel(p.getModelName());
        b.setQuestionCount(p.getQuestionCount());
        b.setStatus(p.getStatus());
        b.setCreateTime(p.getCreateTime());
        return b;
    }

    // ==================== 辅助 ====================

    private String courseNameOf(Long paperId) {
        if (paperId == null) {
            return null;
        }
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null || paper.getCourseOfferingId() == null) {
            return null;
        }
        CourseOffering offering = courseOfferingMapper.selectById(paper.getCourseOfferingId());
        if (offering == null || offering.getCourseId() == null) {
            return null;
        }
        Course course = courseMapper.selectById(offering.getCourseId());
        return course == null ? null : course.getCourseName();
    }

    private BigDecimal scoreOf(Long paperId, Long questionId, Long studentId) {
        ExamQuestionScore s = questionScoreMapper.selectOne(new LambdaQueryWrapper<ExamQuestionScore>()
                .eq(ExamQuestionScore::getExamPaperId, paperId)
                .eq(ExamQuestionScore::getExamQuestionId, questionId)
                .eq(ExamQuestionScore::getStudentId, studentId)
                .last("LIMIT 1"));
        return s == null ? null : s.getScore();
    }

    private String optionsText(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return "（无）";
        }
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                node.forEach(n -> list.add(n.asText()));
                return String.join("；", list);
            }
        } catch (Exception ignored) {
            // 非 JSON 时原样返回
        }
        return optionsJson;
    }

    private String toJson(List<PracticeQuestion> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("练习内容序列化失败：{}", e.getMessage());
            return "[]";
        }
    }

    private void logCall(Long bizId, String model, int promptTokens, int completionTokens,
                         int costMs, String status, String errorMsg) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setBizType("PRACTICE");
            callLog.setBizId(bizId);
            callLog.setModelName(model);
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setTotalTokens(promptTokens + completionTokens);
            callLog.setCostMs(costMs);
            callLog.setStatus(status);
            callLog.setErrorMsg(errorMsg == null ? null : errorMsg.length() > 500
                    ? errorMsg.substring(0, 500) : errorMsg);
            callLog.setUserId(com.gyb.xqfx.security.SecurityUtils.currentUserIdOrNull());
            callLog.setCreateTime(LocalDateTime.now());
            aiCallLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("写入大模型调用日志失败：{}", e.getMessage());
        }
    }

    private String typeName(String type) {
        if (type == null) {
            return "综合";
        }
        return switch (type) {
            case "SINGLE" -> "单选题";
            case "MULTI" -> "多选题";
            case "JUDGE" -> "判断题";
            case "FILL" -> "填空题";
            case "SHORT" -> "简答题";
            case "CALC" -> "计算题";
            default -> type;
        };
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "（未录入）" : s;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 内部类 ====================

    /** 错题（题目维度或学生维度） */
    @Data
    public static class WrongQuestion {
        private Long questionId;
        private String questionNo;
        private String questionType;
        private String knowledgePoint;
        private BigDecimal fullScore;
        private BigDecimal avgScore;
        private BigDecimal scoreRate;
        private String stem;
        /** 该生得分，班级维度为 null */
        private BigDecimal studentScore;
        /** 失分，仅学生维度 */
        private BigDecimal lostScore;
        /** 班级维度：答错人数 */
        private Long wrongCount;
        /** 学生维度：是否有作答记录 */
        private Boolean isWrong;
    }

    /** 生成的一道练习 */
    @Data
    public static class PracticeQuestion {
        private Integer index;
        private String stem;
        private List<String> options = new ArrayList<>();
        private String answer;
        private String analysis;
        private String difficulty;
    }

    /** 一次生成结果 */
    @Data
    public static class PracticeResult {
        private Long practiceId;
        private Long questionId;
        private Long studentId;
        private String courseName;
        private String knowledgePoint;
        private String questionType;
        /** AI 本地模型 / RULE 规则降级 */
        private String sourceType;
        private String model;
        private String status;
        private String errorMsg;
        private Integer costMs;
        private LocalDateTime createTime;
        private List<PracticeQuestion> items = new ArrayList<>();
    }

    /** 练习列表项 */
    @Data
    public static class PracticeBrief {
        private Long practiceId;
        private Long questionId;
        private String courseName;
        private String knowledgePoint;
        private String questionType;
        private String sourceType;
        private String model;
        private Integer questionCount;
        private String status;
        private LocalDateTime createTime;
    }
}
