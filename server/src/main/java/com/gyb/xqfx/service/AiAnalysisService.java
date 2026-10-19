package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.client.RemoteLlmClient;
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
import com.gyb.xqfx.model.vo.BigScreenVO;
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
    private static final String SCENE_WEEKLY_REPORT = "WEEKLY_REPORT";
    private static final String SCENE_INTERVENTION = "INTERVENTION_PLAN";
    private static final String SCENE_NL_QUERY = "NL_QUERY";
    private static final String SCENE_COMMENT = "COMMENT";
    private static final String SCENE_PARENT_NOTICE = "PARENT_NOTICE";

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
    private static final String DEFAULT_WEEKLY_REPORT_TEMPLATE =
            "你是校级学情管理分析师。请基于以下本周预警数据，生成一份预警工作周报，输出要求：\n"
                    + "1 本周总体态势（预警总量、变化趋势）；2 高发预警等级与类型；3 重点学院与班级；"
                    + "4 典型预警案例；5 下步管理建议（按优先级 3~5 条）。\n"
                    + "输出使用中文，条理清晰，只依据给定数据，不得编造。\n\n{context}";
    private static final String DEFAULT_INTERVENTION_TEMPLATE =
            "你是高校辅导员/学业帮扶专家。请针对该学生的预警问题，制定一份可执行的干预帮扶方案，输出要求：\n"
                    + "1 问题诊断（该生当前面临的核心问题与数据依据）；2 干预目标（1~2 个可衡量目标）；"
                    + "3 具体措施（按周排期 4~6 条，注明由谁执行）；4 沟通话术（首次约谈参考话术）；"
                    + "5 家校协同（是否需要通知家长及配合事项）；6 效果评估（评估时间点与指标）。\n"
                    + "输出使用中文，具体可落地，只依据给定数据。\n\n{context}";
    private static final String DEFAULT_NL_QUERY_TEMPLATE =
            "你是高校学情数据问答助手。以下是根据用户问题检索到的学情数据，请组织一段自然语言回答：\n"
                    + "1 直接给出结论（谁/多少/如何）；2 用数据佐证；3 给出 1~2 条简短建议。\n"
                    + "回答控制在 200 字内，通俗易懂，只依据给定数据，不得编造。\n\n用户问题：{question}\n\n检索数据：\n{context}";
    private static final String DEFAULT_COMMENT_TEMPLATE =
            "你是班主任/学业导师。请基于该学生的成绩、出勤与学习行为数据，撰写一份学期评语，输出要求：\n"
                    + "1 总体印象（2~3 句）；2 亮点与进步；3 存在的不足；4 下学期的期望与建议（2~3 条）。\n"
                    + "语气亲切、鼓励为主、具体到数据，避免空泛套话，只依据给定数据。\n\n{context}";
    private static final String DEFAULT_PARENT_NOTICE_TEMPLATE =
            "你是学校辅导员。请基于以下预警信息和学生数据，撰写一份给家长的沟通通知，输出要求：\n"
                    + "1 称呼与问候；2 用通俗易懂的语言说明孩子近期情况（不堆砌专业术语，数据用大白话解释）；"
                    + "3 说明学校已经或即将采取的措施；4 请家长配合的事项（2~3 条）；5 结尾与联系方式占位。\n"
                    + "语气温和、尊重家长、具体可执行，只依据给定数据，不得编造。\n\n{context}";

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
    private final RemoteLlmClient remoteLlmClient;
    private final KnowledgeService knowledgeService;
    private final BigScreenService bigScreenService;

    @Value("${xqfx.ollama.enabled:true}")
    private boolean llmEnabled;

    @Value("${xqfx.ollama.fallback-to-rule:true}")
    private boolean fallbackToRule;

    @Value("${xqfx.ollama.fallback-to-cloud:false}")
    private boolean fallbackToCloud;

    /** auto=本地优先云端兜底 | local=仅本地 | cloud=仅云端 */
    @Value("${xqfx.llm.provider:auto}")
    private String llmProvider;

    /**
     * 大模型对话路由：本地优先 →（可选）云端兜底。
     *
     * <p>provider=cloud 时直接走云端；provider=local/auto 时先本地 Ollama，
     * 本地不可用且开启 fallback-to-cloud 时转云端；云端也未配置/失败时返回 null，
     * 由上层继续走规则化降级。</p>
     *
     * @return 任一大模型通道的成功结果；全部不可用时返回 null
     */
    private OllamaClient.ChatResult chatWithFallback(String system, String prompt) {
        if ("cloud".equals(llmProvider)) {
            return remoteLlmClient.chat(system, prompt);
        }
        OllamaClient.ChatResult local = ollamaClient.chat(system, prompt);
        if (local != null) {
            return local;
        }
        if (fallbackToCloud) {
            return remoteLlmClient.chat(system, prompt);
        }
        return null;
    }

    /**
     * 流式对话路由：本地优先 →（可选）云端兜底。
     *
     * @return 是否成功启用任一大模型流式回复
     */
    private boolean streamChatWithFallback(String system, String prompt, Consumer<String> onChunk) {
        if ("cloud".equals(llmProvider)) {
            return remoteLlmClient.chatStream(system, prompt, onChunk);
        }
        if (ollamaClient.chatStream(system, prompt, onChunk)) {
            return true;
        }
        if (fallbackToCloud) {
            return remoteLlmClient.chatStream(system, prompt, onChunk);
        }
        return false;
    }

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
            OllamaClient.ChatResult chat = chatWithFallback(system, prompt);
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
            OllamaClient.ChatResult chat = chatWithFallback(system, prompt);
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
        if (llmEnabled && streamChatWithFallback(system, prompt, onChunk)) {
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
            OllamaClient.ChatResult chat = chatWithFallback(system, prompt);
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
     * 通用模型调用骨架：成功路径 + 规则化降级 + 调用审计
     */
    private AiResult callAi(String scene, String system, String prompt, Long bizId, Long userId,
                            java.util.function.Supplier<String> ruleFallback) {
        long start = System.currentTimeMillis();
        AiResult result = new AiResult();
        if (llmEnabled) {
            OllamaClient.ChatResult chat = chatWithFallback(system, prompt);
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
        result.content = fallbackToRule ? ruleFallback.get()
                : "大模型服务不可用，且未开启规则化降级。";
        logCall(scene, bizId, "rule-based", templateId(scene), 0, 0,
                (int) (System.currentTimeMillis() - start), result.status, "模型不可用", userId);
        return result;
    }

    /**
     * ① 预警工作周报：聚合全校预警态势生成文字周报
     */
    public AiResult generateWeeklyReport(Long userId) {
        BigScreenVO vo = bigScreenService.overview();
        StringBuilder ctx = new StringBuilder();
        ctx.append("本周预警总量：").append(vo.getWarningTrend().stream().mapToLong(BigScreenVO.TrendItem::getCount).sum())
                .append(" 条；待处理预警 ").append(vo.getOverview().getActiveWarning())
                .append(" 条，高危 ").append(vo.getOverview().getSevereWarning())
                .append(" 条，进行中工单 ").append(vo.getOverview().getOpenTicket()).append(" 条。\n");
        ctx.append("近 8 周趋势：");
        vo.getWarningTrend().forEach(t -> ctx.append(t.getWeek()).append("=").append(t.getCount()).append("，"));
        ctx.append("\n等级分布：");
        vo.getWarningLevelDist().forEach(n -> ctx.append(n.getName()).append(n.getValue()).append("，"));
        ctx.append("\n类型分布：");
        vo.getWarningTypeDist().forEach(n -> ctx.append(n.getName()).append(n.getValue()).append("，"));
        ctx.append("\n学院排行：");
        vo.getCollegeRanking().forEach(r -> ctx.append(r.getCollegeName()).append("(预警")
                .append(r.getWarningCount()).append("/高危").append(r.getSevereCount()).append(")，"));
        ctx.append("\n最新预警案例：");
        vo.getLatestWarnings().forEach(w -> ctx.append(w.getStudentName()).append("：")
                .append(w.getTitle()).append("；"));

        String prompt = renderTemplate(loadTemplate(SCENE_WEEKLY_REPORT, DEFAULT_WEEKLY_REPORT_TEMPLATE),
                Map.of("context", ctx.toString()));
        return callAi(SCENE_WEEKLY_REPORT, "你是严谨、客观的校级学情管理分析师，输出中文。",
                prompt, null, userId,
                () -> "【本地大模型暂不可用，以下为规则化预警周报】\n\n"
                        + ctx + "\n\n管理建议：1. 优先处置红色/橙色预警；2. 对预警集中的学院开展专项分析；3. 跟进进行中工单，避免超时。");
    }

    /**
     * ② AI 干预帮扶方案：预警 → 方案文本，教师确认后落工单
     */
    public AiResult generateInterventionPlan(Long warningId, Long userId) {
        WarningRecord w = warningRecordMapper.selectById(warningId);
        if (w == null) {
            throw new IllegalArgumentException("预警不存在：" + warningId);
        }
        StudentContext ctx = loadContext(w.getStudentId(), w.getTermId());
        String context = "预警编号：" + w.getWarningNo() + "；类型：" + w.getWarningType()
                + "；等级：" + levelText(w.getWarningLevel()) + "；标题：" + w.getTitle()
                + "；触发详情：" + w.getTriggerDetail()
                + "；风险概率：" + (w.getRiskProbability() == null ? "-" : w.getRiskProbability().toPlainString())
                + "\n\n" + ctx.toText();
        String prompt = renderTemplate(loadTemplate(SCENE_INTERVENTION, DEFAULT_INTERVENTION_TEMPLATE),
                Map.of("context", context));
        return callAi(SCENE_INTERVENTION, "你是经验丰富的高校辅导员，方案要具体、可执行、有排期。",
                prompt, warningId, userId,
                () -> "【本地大模型暂不可用，以下为规则化干预方案】\n\n"
                        + "问题诊断：该生" + levelText(w.getWarningLevel()) + "预警（" + w.getTitle() + "），"
                        + (ctx.failCount > 0 ? "存在 " + ctx.failCount + " 门不及格课程；" : "")
                        + (ctx.attendanceRate < 0.85 ? "出勤率 " + String.format("%.1f%%", ctx.attendanceRate * 100) + " 偏低；" : "")
                        + "预测风险概率 " + String.format("%.2f", ctx.riskProbability) + "。\n"
                        + "干预目标：两周内与辅导员完成首次约谈，制定学业提升计划。\n"
                        + "措施：1. 本周内约谈学生了解原因并记录；2. 联系任课教师梳理薄弱点；"
                        + "3. 每周跟踪考勤与作业提交；4. 月末评估进展。\n"
                        + "家校协同：必要时通知家长，请家长配合督促。");
    }

    /**
     * ③ 自然语言查学情：意图规则路由 + 数据检索 + AI 组织回答
     */
    public AiResult answerLearningQuery(String question, Long userId) {
        String context = buildQueryContext(question);
        String prompt = renderTemplate(loadTemplate(SCENE_NL_QUERY, DEFAULT_NL_QUERY_TEMPLATE),
                Map.of("question", question, "context", context));
        return callAi(SCENE_NL_QUERY, "你是严谨的高校学情数据问答助手，回答通俗、有数据依据。",
                prompt, null, userId,
                () -> "【本地大模型暂不可用，以下为规则化查询结果】\n\n" + context);
    }

    /**
     * ④ 成绩单 AI 评语
     */
    public AiResult generateComment(Long studentId, Long termId, Long userId) {
        StudentContext ctx = loadContext(studentId, termId);
        String prompt = renderTemplate(loadTemplate(SCENE_COMMENT, DEFAULT_COMMENT_TEMPLATE),
                Map.of("context", ctx.toText()));
        AiResult result = callAi(SCENE_COMMENT, "你是亲切、专业的班主任，评语鼓励为主、具体到数据。",
                prompt, studentId, userId,
                () -> buildRuleBasedComment(ctx));
        result.studentName = ctx.studentName;
        return result;
    }

    /**
     * ⑤ 家长沟通通知：预警 → 家长易懂文案
     */
    public AiResult generateParentNotice(Long warningId, Long userId) {
        WarningRecord w = warningRecordMapper.selectById(warningId);
        if (w == null) {
            throw new IllegalArgumentException("预警不存在：" + warningId);
        }
        StudentContext ctx = loadContext(w.getStudentId(), w.getTermId());
        String context = "预警类型：" + w.getWarningType() + "；等级：" + levelText(w.getWarningLevel())
                + "；标题：" + w.getTitle() + "；触发详情：" + w.getTriggerDetail()
                + "\n\n" + ctx.toText();
        String prompt = renderTemplate(loadTemplate(SCENE_PARENT_NOTICE, DEFAULT_PARENT_NOTICE_TEMPLATE),
                Map.of("context", context));
        return callAi(SCENE_PARENT_NOTICE, "你是温和、尊重家长的高校辅导员，文案通俗、具体、有温度。",
                prompt, warningId, userId,
                () -> "【本地大模型暂不可用，以下为规则化家长通知】\n\n"
                        + "尊敬的家长：您好！\n"
                        + "近期我们关注到" + ctx.studentName + "同学在学业上遇到一些情况："
                        + w.getTitle() + "（" + levelText(w.getWarningLevel()) + "预警）。"
                        + (ctx.failCount > 0 ? "目前有 " + ctx.failCount + " 门课程成绩不理想。" : "")
                        + "学校已安排辅导员跟进，正在制定帮扶计划。\n"
                        + "希望家长能：1. 多与孩子沟通，了解学习上的困难；2. 协助督促孩子按时上课、完成作业；"
                        + "3. 如有需要，随时联系辅导员（电话：请补充）。\n"
                        + "让我们一起帮助孩子尽快回到正常学习轨道。");
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
     * 意图关键词匹配
     */
    private boolean matches(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private String levelText(Integer level) {
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

    /**
     * 规则化评语
     */
    private String buildRuleBasedComment(StudentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.studentName).append("同学本学期整体表现");
        sb.append(ctx.avgScore >= 85 ? "优异" : ctx.avgScore >= 75 ? "良好" : ctx.avgScore >= 60 ? "平稳" : "需要关注")
                .append("，共修读 ").append(ctx.courseCount).append(" 门课程，平均成绩 ")
                .append(String.format("%.1f", ctx.avgScore)).append(" 分。\n\n");
        sb.append("亮点：");
        if (ctx.attendanceRate >= 0.9) {
            sb.append("出勤积极（").append(String.format("%.1f%%", ctx.attendanceRate * 100)).append("），");
        }
        if (ctx.studyMinutes > 600) {
            sb.append("在线学习投入充足（累计 ").append(ctx.studyMinutes).append(" 分钟），");
        }
        sb.append("最高分达到 ").append(String.format("%.1f", ctx.maxScore)).append(" 分。\n\n");
        sb.append("不足：");
        if (ctx.failCount > 0) {
            sb.append(ctx.failCount).append(" 门课程不及格，");
        }
        if (ctx.attendanceRate < 0.85) {
            sb.append("出勤率偏低，");
        }
        sb.append("学习上仍有提升空间。\n\n下学期建议：");
        sb.append(buildRuleBasedAdvice(ctx));
        return sb.toString();
    }

    /**
     * 自然语言查询的意图识别与数据检索（规则兜底，不让模型直接写 SQL）
     */
    private String buildQueryContext(String question) {
        if (question == null || question.isBlank()) {
            return "（问题为空）";
        }
        StringBuilder sb = new StringBuilder();
        if (matches(question, "挂科", "不及格", "风险", "危险", "预警学生", "预警")) {
            List<WarningRecord> ws = warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                    .eq(WarningRecord::getStatus, "ACTIVE")
                    .orderByDesc(WarningRecord::getWarningLevel).last("limit 5"));
            sb.append("待处理预警前 5：\n");
            if (ws.isEmpty()) {
                sb.append("当前无待处理预警。");
            } else {
                ws.forEach(w -> sb.append("- ").append(studentName(w.getStudentId())).append("：")
                        .append(levelText(w.getWarningLevel())).append("预警 · ")
                        .append(w.getTitle()).append("\n"));
            }
        } else if (matches(question, "下滑", "退步", "下降", "波动", "高风")) {
            List<PredictionResult> ps = predictionResultMapper.selectList(
                    new LambdaQueryWrapper<PredictionResult>()
                            .orderByDesc(PredictionResult::getRiskProbability).last("limit 5"));
            sb.append("风险概率最高的 5 名学生：\n");
            if (ps.isEmpty()) {
                sb.append("暂无预测数据。");
            } else {
                ps.forEach(p -> sb.append("- ").append(studentName(p.getStudentId())).append("：风险概率 ")
                        .append(p.getRiskProbability() == null ? "-" : p.getRiskProbability().toPlainString())
                        .append("\n"));
            }
        } else if (matches(question, "出勤", "缺勤", "迟到", "旷课")) {
            List<Attendance> bad = attendanceMapper.selectList(
                    new LambdaQueryWrapper<Attendance>()
                            .in(Attendance::getStatus, "ABSENT", "LATE", "EARLY")
                            .orderByDesc(Attendance::getId).last("limit 8"));
            sb.append("最近缺勤/迟到/早退记录：\n");
            if (bad.isEmpty()) {
                sb.append("最近无缺勤记录。");
            } else {
                bad.forEach(a -> sb.append("- ").append(studentName(a.getStudentId())).append("：")
                        .append(a.getStatus()).append(" ").append(a.getCheckDate()).append("\n"));
            }
        } else if (matches(question, "优秀", "高分", "排名", "前", "最好")) {
            List<Score> tops = scoreMapper.selectList(
                    new LambdaQueryWrapper<Score>().orderByDesc(Score::getTotalScore).last("limit 5"));
            sb.append("成绩最高的 5 条记录：\n");
            if (tops.isEmpty()) {
                sb.append("暂无成绩数据。");
            } else {
                tops.forEach(s -> sb.append("- ").append(studentName(s.getStudentId())).append("：")
                        .append(s.getTotalScore() == null ? "-" : s.getTotalScore().toPlainString())
                        .append(" 分\n"));
            }
        } else {
            Term term = termMapper.selectOne(new LambdaQueryWrapper<Term>().eq(Term::getIsCurrent, 1).last("limit 1"));
            sb.append("全校概览：学生总数 ")
                    .append(studentMapper.selectCount(null))
                    .append("；当前学期 ")
                    .append(term == null ? "-" : term.getTermName())
                    .append("；待处理预警 ")
                    .append(warningRecordMapper.selectCount(new LambdaQueryWrapper<WarningRecord>()
                            .eq(WarningRecord::getStatus, "ACTIVE")))
                    .append(" 条。");
        }
        return sb.toString();
    }

    private String studentName(Long studentId) {
        Student s = studentMapper.selectById(studentId);
        return s == null ? ("学生#" + studentId) : s.getName();
    }

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
