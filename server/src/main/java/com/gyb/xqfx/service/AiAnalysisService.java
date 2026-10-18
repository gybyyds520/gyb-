package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.entity.AiCallLog;
import com.gyb.xqfx.entity.AiPromptTemplate;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.OnlineBehavior;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.ReportRecord;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.entity.WarningRecord;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型学情分析服务：上下文组装 → 提示词渲染 → 模型调用 → 调用审计 → 降级兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final String SCENE_REPORT = "REPORT";
    private static final String SCENE_ADVICE = "ADVICE";
    private static final String SCENE_CHAT = "CHAT";
    private static final String SCENE_ATTRIBUTION = "ATTRIBUTION";

    private static final String DEFAULT_REPORT_TEMPLATE =
            "你是高校学业分析师。请基于以下学情数据，生成一份结构化的学情分析报告，"
                    + "包含：1 总体评价；2 优势与亮点；3 风险点；4 改进建议（不少于3条）。\n\n{context}";
    private static final String DEFAULT_ADVICE_TEMPLATE =
            "你是学业指导老师。请针对该学生的薄弱项，给出 3~5 条具体、可执行的学业改进建议。\n\n{context}";
    private static final String DEFAULT_CHAT_TEMPLATE =
            "你是高校学情助手，请严格依据参考资料回答；资料中没有的内容请明确说明“资料未提及”。\n\n"
                    + "参考资料：\n{knowledge}\n\n学情数据：\n{context}\n\n问题：{question}";
    private static final String DEFAULT_ATTRIBUTION_TEMPLATE =
            "你是高校学业数据分析师。请基于以下学情上下文做因果归因分析，输出要求：\n"
                    + "1 现象概述（发生了什么）；2 可能成因（结合数据证据，列出 2~4 条，区分主因与次要因素）；"
                    + "3 佐证与反证（哪些数据支持该判断）；4 干预建议（按优先级给出 2~3 条可执行措施）。\n"
                    + "输出使用中文，条理清晰，只依据给定数据，不得编造。\n\n{context}";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final TermMapper termMapper;
    private final ClazzMapper clazzMapper;
    private final AiPromptTemplateMapper templateMapper;
    private final AiCallLogMapper callLogMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final OllamaClient ollamaClient;
    private final KnowledgeService knowledgeService;

    @Value("${xqfx.ollama.enabled:true}")
    private boolean llmEnabled;

    @Value("${xqfx.ollama.fallback-to-rule:true}")
    private boolean fallbackToRule;

    /**
     * 生成学生学情分析报告
     */
    public AiResult generateReport(Long studentId, Long termId, Long userId) {
        StudentContext ctx = loadContext(studentId, termId);
        String context = ctx.toText();
        String template = loadTemplate(SCENE_REPORT, DEFAULT_REPORT_TEMPLATE);
        String prompt = renderTemplate(template, Map.of("context", context));
        String system = "你是严谨、客观的高校学业分析师，输出使用中文，条理清晰，避免空泛套话。";

        long start = System.currentTimeMillis();
        AiResult result = new AiResult();
        result.studentName = ctx.studentName;

        if (llmEnabled) {
            OllamaClient.ChatResult chat = ollamaClient.chat(system, prompt);
            if (chat != null) {
                result.content = chat.content;
                result.model = chat.model;
                result.status = "SUCCESS";
                result.totalTokens = chat.totalTokens();
                logCall("REPORT", studentId, chat.model, templateId(SCENE_REPORT),
                        chat.promptTokens, chat.completionTokens,
                        (int) (System.currentTimeMillis() - start), "SUCCESS", null, userId);
                saveReport(studentId, termId, chat.model);
                return result;
            }
        }

        if (!fallbackToRule) {
            result.status = "FAILED";
            result.content = "大模型服务不可用，且未开启规则化降级。";
            logCall("REPORT", studentId, null, null, 0, 0,
                    (int) (System.currentTimeMillis() - start), "FAILED", "模型不可用", userId);
            return result;
        }

        result.content = buildRuleBasedReport(ctx);
        result.model = "rule-based";
        result.status = "FALLBACK";
        logCall("REPORT", studentId, "rule-based", null, 0, 0,
                (int) (System.currentTimeMillis() - start), "FALLBACK", "模型不可用，已降级", userId);
        saveReport(studentId, termId, "rule-based");
        return result;
    }

    /**
     * 生成个性化改进建议
     */
    public AiResult generateAdvice(Long studentId, Long termId, Long userId) {
        StudentContext ctx = loadContext(studentId, termId);
        String template = loadTemplate(SCENE_ADVICE, DEFAULT_ADVICE_TEMPLATE);
        String prompt = renderTemplate(template, Map.of("context", ctx.toText()));
        String system = "你是经验丰富的学业指导教师，建议要具体、可操作、可衡量。";

        long start = System.currentTimeMillis();
        AiResult result = new AiResult();
        result.studentName = ctx.studentName;

        if (llmEnabled) {
            OllamaClient.ChatResult chat = ollamaClient.chat(system, prompt);
            if (chat != null) {
                result.content = chat.content;
                result.model = chat.model;
                result.status = "SUCCESS";
                result.totalTokens = chat.totalTokens();
                logCall("ADVICE", studentId, chat.model, templateId(SCENE_ADVICE),
                        chat.promptTokens, chat.completionTokens,
                        (int) (System.currentTimeMillis() - start), "SUCCESS", null, userId);
                return result;
            }
        }

        result.content = buildRuleBasedAdvice(ctx);
        result.model = "rule-based";
        result.status = fallbackToRule ? "FALLBACK" : "FAILED";
        logCall("ADVICE", studentId, "rule-based", null, 0, 0,
                (int) (System.currentTimeMillis() - start), result.status, "模型不可用", userId);
        return result;
    }

    /**
     * 流式学情问答（RAG）
     *
     * @param onChunk 文本片段回调
     * @return 是否使用了大模型（false 表示已降级）
     */
    public boolean streamChat(String question, Long studentId, Long termId, Long userId, Consumer<String> onChunk) {
        String context = studentId == null ? "（未指定学生）" : loadContext(studentId, termId).toText();
        List<String> knowledge = knowledgeService.search(question, 3);
        String knowledgeText = knowledge.isEmpty() ? "（知识库中暂无相关资料）" : String.join("\n\n", knowledge);
        String template = loadTemplate(SCENE_CHAT, DEFAULT_CHAT_TEMPLATE);
        String prompt = renderTemplate(template, Map.of(
                "knowledge", knowledgeText, "context", context, "question", question));
        String system = "你是严谨的高校学情助手，回答简洁、基于事实，不做主观臆断。";

        long start = System.currentTimeMillis();
        if (llmEnabled && ollamaClient.chatStream(system, prompt, onChunk)) {
            logCall("CHAT", studentId, null, null, 0, 0,
                    (int) (System.currentTimeMillis() - start), "SUCCESS", null, userId);
            return true;
        }

        // 降级：直接返回检索到的资料与规则化提示
        StringBuilder sb = new StringBuilder();
        sb.append("【本地大模型暂不可用，以下为知识库检索结果与规则化答复】\n\n");
        if (knowledge.isEmpty()) {
            sb.append("知识库中未检索到与“").append(question).append("”相关的内容。");
        } else {
            sb.append("相关资料：\n");
            for (int i = 0; i < knowledge.size(); i++) {
                sb.append(i + 1).append(". ").append(knowledge.get(i)).append("\n\n");
            }
        }
        onChunk.accept(sb.toString());
        logCall("CHAT", studentId, "rule-based", null, 0, 0,
                (int) (System.currentTimeMillis() - start), "FALLBACK", "模型不可用", userId);
        return false;
    }

    /**
     * 学情事件因果归因（预警归因 / 偏科归因等）
     *
     * <p>调用方组装数据上下文后传入，由大模型生成因果链解读；
     * 模型不可用时降级为确定性说明。</p>
     *
     * @param scene       业务场景编码（如 WARNING_ATTRIBUTION / BIAS_ATTRIBUTION）
     * @param contextText 数据上下文文本
     * @param bizId       业务对象ID（预警ID/学生ID）
     * @return 归因结果
     */
    public AiResult generateAttribution(String scene, String contextText, Long bizId, Long userId) {
        String template = loadTemplate(scene, DEFAULT_ATTRIBUTION_TEMPLATE);
        String prompt = renderTemplate(template, Map.of("context",
                contextText == null ? "（无上下文）" : contextText));
        String system = "你是严谨、客观的高校学业数据分析师，输出中文，因果归因必须基于给定数据，区分事实与推测。";

        long start = System.currentTimeMillis();
        AiResult result = new AiResult();
        if (llmEnabled) {
            OllamaClient.ChatResult chat = ollamaClient.chat(system, prompt);
            if (chat != null) {
                result.content = chat.content;
                result.model = chat.model;
                result.status = "SUCCESS";
                result.totalTokens = chat.totalTokens();
                logCall(scene, bizId, chat.model, templateId(scene),
                        chat.promptTokens, chat.completionTokens,
                        (int) (System.currentTimeMillis() - start), "SUCCESS", null, userId);
                return result;
            }
        }
        result.model = "rule-based";
        result.status = fallbackToRule ? "FALLBACK" : "FAILED";
        result.content = fallbackToRule
                ? "【本地大模型暂不可用，自动归因已降级】\n\n无法自动生成因果归因。请结合上下文中的预警内容、成绩与画像数据人工研判：\n"
                        + "1. 现象：以预警内容与指标数值为准；\n2. 成因：检查成绩下滑、出勤率、学习投入与风险概率等直接证据；\n"
                        + "3. 干预：优先处理等级最高的预警，并安排谈心或学业帮扶。"
                : "大模型服务不可用，且未开启规则化降级。";
        logCall(scene, bizId, "rule-based", templateId(scene), 0, 0,
                (int) (System.currentTimeMillis() - start), result.status, "模型不可用", userId);
        return result;
    }

    // ==================== 上下文与模板 ====================

    /**
     * 组装学生学情上下文
     */
    public StudentContext loadContext(Long studentId, Long termId) {
        StudentContext ctx = new StudentContext();
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在：" + studentId);
        }
        ctx.studentId = studentId;
        ctx.studentNo = student.getStudentNo();
        ctx.studentName = student.getName();

        if (student.getClazzId() != null) {
            var clazz = clazzMapper.selectById(student.getClazzId());
            if (clazz != null) {
                ctx.clazzName = clazz.getClazzName();
            }
        }
        if (termId != null) {
            Term term = termMapper.selectById(termId);
            ctx.termName = term == null ? null : term.getTermName();
        }

        LambdaQueryWrapper<Score> scoreWrapper = new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId);
        if (termId != null) {
            scoreWrapper.eq(Score::getTermId, termId);
        }
        List<Score> scores = scoreMapper.selectList(scoreWrapper);
        ctx.courseCount = scores.size();
        ctx.avgScore = avg(scores.stream().map(Score::getTotalScore).filter(Objects::nonNull).toList());
        ctx.maxScore = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        ctx.minScore = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        ctx.failCount = (int) scores.stream()
                .filter(s -> s.getTotalScore() != null && s.getTotalScore().compareTo(new BigDecimal("60")) < 0)
                .count();

        LambdaQueryWrapper<Attendance> attWrapper = new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStudentId, studentId);
        if (termId != null) {
            attWrapper.eq(Attendance::getTermId, termId);
        }
        List<Attendance> attendances = attendanceMapper.selectList(attWrapper);
        ctx.attendanceTotal = attendances.size();
        ctx.absentCount = (int) attendances.stream()
                .filter(a -> "ABSENT".equals(a.getStatus()) || "EARLY".equals(a.getStatus())).count();
        ctx.attendanceRate = attendances.isEmpty() ? 1.0
                : (double) (attendances.size() - ctx.absentCount) / attendances.size();

        LambdaQueryWrapper<OnlineBehavior> behWrapper = new LambdaQueryWrapper<OnlineBehavior>()
                .eq(OnlineBehavior::getStudentId, studentId);
        if (termId != null) {
            behWrapper.eq(OnlineBehavior::getTermId, termId);
        }
        List<OnlineBehavior> behaviors = onlineBehaviorMapper.selectList(behWrapper);
        ctx.studyMinutes = (int) behaviors.stream()
                .mapToInt(b -> b.getStudyMinutes() == null ? 0 : b.getStudyMinutes()).sum();

        LambdaQueryWrapper<PredictionResult> predWrapper = new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getStudentId, studentId);
        if (termId != null) {
            predWrapper.eq(PredictionResult::getTermId, termId);
        }
        List<PredictionResult> preds = predictionResultMapper.selectList(predWrapper);
        if (!preds.isEmpty()) {
            PredictionResult p = preds.get(0);
            ctx.riskProbability = p.getRiskProbability() == null ? 0 : p.getRiskProbability().doubleValue();
            ctx.riskLevel = p.getRiskLevel();
        }

        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId));
        ctx.warningCount = warnings.size();
        ctx.maxWarningLevel = warnings.stream().map(WarningRecord::getWarningLevel)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);

        return ctx;
    }

    /**
     * 渲染模板：将 {var} 替换为实际值，未提供的变量替换为空串
     */
    public String renderTemplate(String template, Map<String, Object> vars) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String loadTemplate(String scene, String defaultTemplate) {
        List<AiPromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<AiPromptTemplate>()
                        .eq(AiPromptTemplate::getScene, scene)
                        .eq(AiPromptTemplate::getEnabled, 1)
                        .orderByDesc(AiPromptTemplate::getVersion));
        return templates.isEmpty() ? defaultTemplate : templates.get(0).getContent();
    }

    private Long templateId(String scene) {
        List<AiPromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<AiPromptTemplate>()
                        .eq(AiPromptTemplate::getScene, scene)
                        .eq(AiPromptTemplate::getEnabled, 1)
                        .orderByDesc(AiPromptTemplate::getVersion));
        return templates.isEmpty() ? null : templates.get(0).getId();
    }

    // ==================== 规则化兜底 ====================

    /**
     * 规则化报告：模型不可用时的确定性输出
     */
    public String buildRuleBasedReport(StudentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 学情分析报告（规则化生成）\n\n");
        sb.append("**学生**：").append(ctx.studentName).append("（").append(ctx.studentNo).append("）");
        if (ctx.clazzName != null) {
            sb.append("　**班级**：").append(ctx.clazzName);
        }
        if (ctx.termName != null) {
            sb.append("　**学期**：").append(ctx.termName);
        }
        sb.append("\n\n");

        sb.append("## 一、总体评价\n");
        sb.append("本学期共修读 ").append(ctx.courseCount).append(" 门课程，平均成绩 ")
                .append(String.format("%.1f", ctx.avgScore)).append(" 分，");
        sb.append(ctx.failCount > 0 ? "存在 " + ctx.failCount + " 门不及格课程，学业状况需要关注。" : "全部课程合格，学业状况良好。");
        sb.append("\n\n");

        sb.append("## 二、优势与亮点\n");
        sb.append("- 最高分 ").append(String.format("%.1f", ctx.maxScore)).append(" 分");
        if (ctx.attendanceRate >= 0.9) {
            sb.append("\n- 出勤率良好（").append(String.format("%.1f%%", ctx.attendanceRate * 100)).append("）");
        }
        if (ctx.studyMinutes > 600) {
            sb.append("\n- 在线学习投入较高，累计 ").append(ctx.studyMinutes).append(" 分钟");
        }
        sb.append("\n\n");

        sb.append("## 三、风险点\n");
        List<String> risks = new ArrayList<>();
        if (ctx.failCount > 0) {
            risks.add("存在 " + ctx.failCount + " 门不及格课程");
        }
        if (ctx.attendanceRate < 0.8) {
            risks.add("出勤率偏低（" + String.format("%.1f%%", ctx.attendanceRate * 100) + "）");
        }
        if (ctx.avgScore < 60) {
            risks.add("平均成绩低于及格线");
        }
        if (ctx.riskProbability >= 0.6) {
            risks.add("模型预测风险概率较高（" + String.format("%.2f", ctx.riskProbability) + "）");
        }
        if (risks.isEmpty()) {
            sb.append("- 暂未发现显著风险点\n");
        } else {
            risks.forEach(r -> sb.append("- ").append(r).append("\n"));
        }
        sb.append("\n");

        sb.append("## 四、改进建议\n").append(buildRuleBasedAdvice(ctx));
        return sb.toString();
    }

    /**
     * 规则化建议
     */
    public String buildRuleBasedAdvice(StudentContext ctx) {
        List<String> advice = new ArrayList<>();
        if (ctx.failCount > 0) {
            advice.add("针对 " + ctx.failCount + " 门不及格课程，建议两周内联系任课教师梳理知识薄弱点并制定重修计划。");
        }
        if (ctx.attendanceRate < 0.85) {
            advice.add("出勤率偏低，建议辅导员约谈了解原因，并建立每周出勤打卡跟踪。");
        }
        if (ctx.studyMinutes < 300) {
            advice.add("在线学习时长不足，建议每周安排至少 5 小时的在线课程学习并按时提交作业。");
        }
        if (ctx.avgScore < 70) {
            advice.add("平均成绩处于中下游，建议加入朋辈学习小组，每周完成一次知识点复盘。");
        }
        if (ctx.riskProbability >= 0.6) {
            advice.add("预测风险较高，建议纳入重点帮扶名单，每月跟踪一次学习进展。");
        }
        if (advice.isEmpty()) {
            advice.add("当前学业状况良好，建议保持现有学习节奏，并适当参与学科竞赛或科研项目提升综合能力。");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < advice.size(); i++) {
            sb.append(i + 1).append(". ").append(advice.get(i)).append("\n");
        }
        return sb.toString();
    }

    private double avg(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private void logCall(String bizType, Long bizId, String model, Long templateId,
                         int promptTokens, int completionTokens, int costMs,
                         String status, String errorMsg, Long userId) {
        try {
            AiCallLog logEntity = new AiCallLog();
            logEntity.setBizType(bizType);
            logEntity.setBizId(bizId);
            logEntity.setModelName(model);
            logEntity.setTemplateId(templateId);
            logEntity.setPromptTokens(promptTokens);
            logEntity.setCompletionTokens(completionTokens);
            logEntity.setTotalTokens(promptTokens + completionTokens);
            logEntity.setCostMs(costMs);
            logEntity.setStatus(status);
            logEntity.setErrorMsg(errorMsg);
            logEntity.setUserId(userId);
            logEntity.setCreateTime(LocalDateTime.now());
            callLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("写入 AI 调用日志失败：{}", e.getMessage());
        }
    }

    private void saveReport(Long studentId, Long termId, String model) {
        try {
            ReportRecord record = new ReportRecord();
            record.setReportType("STUDENT");
            record.setBizId(studentId);
            record.setTermId(termId);
            record.setModelName(model);
            record.setFileFormat("HTML");
            record.setStatus("SUCCESS");
            reportRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("写入报告记录失败：{}", e.getMessage());
        }
    }

    /** 学生学情上下文 */
    @Data
    public static class StudentContext {
        private Long studentId;
        private String studentNo;
        private String studentName;
        private String clazzName;
        private String termName;

        private int courseCount;
        private double avgScore;
        private double maxScore;
        private double minScore;
        private int failCount;

        private int attendanceTotal;
        private int absentCount;
        private double attendanceRate;

        private int studyMinutes;
        private double riskProbability;
        private Integer riskLevel;
        private int warningCount;
        private int maxWarningLevel;

        public String toText() {
            return String.format(
                    "学生：%s（学号 %s）%s%s%n"
                            + "成绩：修读 %d 门，平均 %.1f 分，最高 %.1f，最低 %.1f，不及格 %d 门%n"
                            + "考勤：记录 %d 次，缺勤/早退 %d 次，出勤率 %.1f%%%n"
                            + "在线学习：累计 %d 分钟%n"
                            + "预测：风险概率 %.2f，风险等级 %s%n"
                            + "预警：历史 %d 条，最高等级 %d",
                    studentName, studentNo,
                    clazzName == null ? "" : "，班级 " + clazzName,
                    termName == null ? "" : "，学期 " + termName,
                    courseCount, avgScore, maxScore, minScore, failCount,
                    attendanceTotal, absentCount, attendanceRate * 100,
                    studyMinutes,
                    riskProbability, riskLevelText(riskLevel),
                    warningCount, maxWarningLevel);
        }

        private String riskLevelText(Integer level) {
            if (level == null) {
                return "未知";
            }
            return switch (level) {
                case 4 -> "红色";
                case 3 -> "橙色";
                case 2 -> "黄色";
                default -> "蓝色";
            };
        }
    }

    /** AI 生成结果 */
    @Data
    public static class AiResult {
        private String content;
        private String model;
        private String status;
        private String studentName;
        private int totalTokens;
    }
}
