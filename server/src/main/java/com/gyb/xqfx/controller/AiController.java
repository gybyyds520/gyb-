package com.gyb.xqfx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.client.RemoteLlmClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.AiCallLog;
import com.gyb.xqfx.entity.AiPromptTemplate;
import com.gyb.xqfx.entity.KnowledgeDoc;
import com.gyb.xqfx.entity.NotifyMessage;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.AiCallLogMapper;
import com.gyb.xqfx.mapper.AiPromptTemplateMapper;
import com.gyb.xqfx.mapper.NotifyMessageMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.AiAnalysisService;
import com.gyb.xqfx.service.DataScopeService;
import com.gyb.xqfx.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 大模型学情分析与知识问答
 */
@Tag(name = "AI 学情分析")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiAnalysisService aiAnalysisService;
    private final KnowledgeService knowledgeService;
    private final DataScopeService dataScopeService;
    private final OllamaClient ollamaClient;
    private final RemoteLlmClient remoteLlmClient;
    private final AiPromptTemplateMapper templateMapper;
    private final AiCallLogMapper callLogMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final StudentParentMapper studentParentMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${xqfx.llm.provider:auto}")
    private String llmProvider;

    @Operation(summary = "大模型服务健康检查（含 Provider 状态）")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean localAvailable = ollamaClient.isAvailable();
        boolean cloudConfigured = remoteLlmClient.isConfigured();
        String activeProvider = localAvailable ? "LOCAL" : (cloudConfigured ? "CLOUD" : "RULE_FALLBACK");
        Map<String, Object> data = new HashMap<>();
        data.put("localModel", llmProvider == null || !"cloud".equals(llmProvider) ? "llama3.2:3b" : "cloud-only");
        data.put("localAvailable", localAvailable);
        data.put("cloudConfigured", cloudConfigured);
        data.put("cloudAvailable", cloudConfigured);
        data.put("activeProvider", activeProvider);
        data.put("providerMode", llmProvider);
        return Result.success(data,
                localAvailable ? "本地 Ollama 服务可用" : (cloudConfigured ? "本地不可用，已配置云端通道" : "本地不可用，将使用规则化降级"));
    }

    @Operation(summary = "本机已安装模型列表")
    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.success(ollamaClient.listModels());
    }

    @Operation(summary = "生成学生学情分析报告")
    @PostMapping("/report")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> report(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId,
                                                     @RequestParam(required = false) Long userId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(aiAnalysisService.generateReport(studentId, termId, userId));
    }

    @Operation(summary = "生成个性化改进建议")
    @PostMapping("/advice")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> advice(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId,
                                                     @RequestParam(required = false) Long userId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(aiAnalysisService.generateAdvice(studentId, termId, userId));
    }

    @Operation(summary = "AI 学科答疑（本地大模型，学生/家长/教师通用）")
    @PostMapping("/tutor")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Map<String, Object>> tutor(@RequestParam String question,
                                             @RequestParam(required = false) String subject,
                                             @RequestParam(required = false) String stage) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入要解答的问题");
        }
        if (!ollamaClient.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本地大模型不可用，请先启动 Ollama");
        }
        String stageText = stage == null || stage.isBlank() ? "通用" : stage;
        String subjectText = subject == null || subject.isBlank() ? "通用学科" : subject;
        String systemPrompt = "你是一位耐心的" + stageText + "「" + subjectText + "」学科辅导老师。"
                + "请用清晰、逐步的讲解帮助学生理解题目，给出解题思路、关键步骤与答案；"
                + "对于不会做的难题，先引导思考方向再给详细解答；回答控制在 400 字以内，语言简洁易懂。";
        OllamaClient.ChatResult res = ollamaClient.chat(systemPrompt, question.trim());
        Map<String, Object> data = new HashMap<>();
        data.put("content", res.content);
        data.put("model", res.model);
        data.put("subject", subjectText);
        // 记录调用日志（供管理端 AI 日志查看；问题内容记入 errorMsg 便于溯源）
        try {
            AiCallLog log = new AiCallLog();
            log.setBizType("TUTOR");
            log.setModelName(res.model);
            log.setPromptTokens(res.promptTokens);
            log.setCompletionTokens(res.completionTokens);
            log.setTotalTokens(res.totalTokens());
            log.setStatus("SUCCESS");
            log.setUserId(SecurityUtils.currentUserIdOrNull());
            log.setErrorMsg(question.length() > 200 ? question.substring(0, 200) : question);
            log.setCreateTime(LocalDateTime.now());
            callLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志写入失败不影响答疑
        }
        return Result.success(data);
    }

    @Operation(summary = "① 预警工作周报（AI 聚合解读）")
    @PostMapping("/weekly-report")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> weeklyReport() {
        return Result.success(aiAnalysisService.generateWeeklyReport(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "③ 自然语言查学情（NL2Query：规则路由 + AI 解释）")
    @PostMapping("/query")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<AiAnalysisService.AiResult> query(@RequestParam String question) {
        return Result.success(aiAnalysisService.answerLearningQuery(question, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "④ 成绩单 AI 评语")
    @PostMapping("/comment")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<AiAnalysisService.AiResult> comment(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(aiAnalysisService.generateComment(studentId, termId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "⑤ 家长沟通通知（AI 生成文案，可一键发送给家长）")
    @PostMapping("/parent-notice/{warningId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> parentNotice(@PathVariable Long warningId,
                                                    @RequestParam(defaultValue = "false") boolean send) {
        AiAnalysisService.AiResult res = aiAnalysisService.generateParentNotice(warningId, SecurityUtils.currentUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("result", res);
        if (send) {
            data.put("sent", sendToParent(warningId, res));
        }
        return Result.success(data);
    }

    private boolean sendToParent(Long warningId, AiAnalysisService.AiResult res) {
        try {
            WarningRecord w = warningRecordMapper.selectById(warningId);
            if (w == null) {
                return false;
            }
            List<StudentParent> parents = studentParentMapper.selectList(
                    new LambdaQueryWrapper<StudentParent>()
                            .eq(StudentParent::getStudentId, w.getStudentId())
                            .eq(StudentParent::getStatus, 1));
            for (StudentParent p : parents) {
                NotifyMessage m = new NotifyMessage();
                m.setUserId(p.getParentUserId());
                m.setMessageType("WARNING");
                m.setTitle("学情预警通知（辅导员来信）");
                m.setContent(res.getContent());
                m.setBizType("WARNING");
                m.setBizId(warningId);
                m.setChannel("IN_APP");
                m.setIsRead(0);
                m.setCreateTime(LocalDateTime.now());
                notifyMessageMapper.insert(m);
            }
            return !parents.isEmpty();
        } catch (Exception e) {
            log.warn("家长通知发送失败：{}", e.getMessage());
            return false;
        }
    }

    @Operation(summary = "学情问答（SSE 流式 + RAG 检索增强）")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String question,
                                 @RequestParam(required = false) Long studentId,
                                 @RequestParam(required = false) Long termId,
                                 @RequestParam(required = false) Long userId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            try {
                aiAnalysisService.streamChat(question, studentId, termId, userId, chunk -> {
                    try {
                        // 以 JSON 单行发送，避免文本中的换行破坏 SSE 格式
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(Map.of("content", chunk))));
                    } catch (Exception e) {
                        log.warn("SSE 发送失败：{}", e.getMessage());
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 学情智能体统一会话入口：按场景路由到现有大模型能力。
     * <p>
     * scene=CHAT 流式 RAG 问答；scene=REPORT 生成学情报告；scene=ADVICE 生成个性化建议。
     * 非流式场景以单次事件推送内容，客户端按同一 SSE 协议消费。
     */
    @Operation(summary = "学情智能体会话（SSE，场景路由）")
    @GetMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentChatStream(@RequestParam(defaultValue = "CHAT") String scene,
                                      @RequestParam(defaultValue = "") String message,
                                      @RequestParam(required = false) Long studentId,
                                      @RequestParam(required = false) Long termId,
                                      @RequestParam(required = false) Long userId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            try {
                switch (scene == null ? "CHAT" : scene.toUpperCase()) {
                    case "REPORT" -> {
                        if (studentId == null) {
                            emitter.send(SseEmitter.event().name("error").data("生成报告需要指定学生"));
                            emitter.complete();
                            return;
                        }
                        AiAnalysisService.AiResult res = aiAnalysisService.generateReport(studentId, termId, userId);
                        emitter.send(SseEmitter.event().name("meta")
                                .data(objectMapper.writeValueAsString(Map.of("model", res.getModel(),
                                        "status", res.getStatus(), "studentName", res.getStudentName(),
                                        "totalTokens", res.getTotalTokens()))));
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(Map.of("content", res.getContent()))));
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    }
                    case "ADVICE" -> {
                        if (studentId == null) {
                            emitter.send(SseEmitter.event().name("error").data("生成建议需要指定学生"));
                            emitter.complete();
                            return;
                        }
                        AiAnalysisService.AiResult res = aiAnalysisService.generateAdvice(studentId, termId, userId);
                        emitter.send(SseEmitter.event().name("meta")
                                .data(objectMapper.writeValueAsString(Map.of("model", res.getModel(),
                                        "status", res.getStatus(), "studentName", res.getStudentName(),
                                        "totalTokens", res.getTotalTokens()))));
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(Map.of("content", res.getContent()))));
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    }
                    default -> aiAnalysisService.streamChat(message, studentId, termId, userId, chunk -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .data(objectMapper.writeValueAsString(Map.of("content", chunk))));
                        } catch (Exception e) {
                            log.warn("SSE 发送失败：{}", e.getMessage());
                        }
                    });
                }
                // 仅默认（CHAT）分支走到此处，REPORT/ADVICE 已提前完成
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("智能体调用失败：" + e.getMessage()));
                } catch (Exception ignored) {
                    // 忽略发送失败的二次异常
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    @Operation(summary = "提示词模板列表（AI 管理配置）")
    @GetMapping("/templates")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<AiPromptTemplate>> templates() {
        return Result.success(templateMapper.selectList(null));
    }

    @Operation(summary = "新增或更新提示词模板（AI 管理配置）")
    @PostMapping("/templates")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Boolean> saveTemplate(@RequestBody AiPromptTemplate template) {
        return Result.success(template.getId() == null
                ? templateMapper.insert(template) > 0
                : templateMapper.updateById(template) > 0);
    }

    @Operation(summary = "知识库文档列表（AI 管理配置）")
    @GetMapping("/knowledge")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<KnowledgeDoc>> knowledgeList() {
        return Result.success(knowledgeService.listDocs());
    }

    @Operation(summary = "录入知识库文档（自动分片与向量化）")
    @PostMapping(value = "/knowledge", consumes = MediaType.TEXT_PLAIN_VALUE)
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Long> addKnowledge(@RequestParam String docName,
                                     @RequestParam(defaultValue = "OTHER") String docType,
                                     @RequestBody String content) {
        return Result.success(knowledgeService.addDoc(docName, docType, content), "文档已入库");
    }

    @Operation(summary = "删除知识库文档")
    @DeleteMapping("/knowledge/{id}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Boolean> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.success(true, "已删除");
    }

    @Operation(summary = "大模型调用日志（AI 管理配置，含提问内容）")
    @GetMapping("/logs")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<AiCallLog>> logs() {
        return Result.success(callLogMapper.selectList(null));
    }
}
