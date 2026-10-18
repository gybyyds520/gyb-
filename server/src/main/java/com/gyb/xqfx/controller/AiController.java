package com.gyb.xqfx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.AiCallLog;
import com.gyb.xqfx.entity.AiPromptTemplate;
import com.gyb.xqfx.entity.KnowledgeDoc;
import com.gyb.xqfx.mapper.AiCallLogMapper;
import com.gyb.xqfx.mapper.AiPromptTemplateMapper;
import com.gyb.xqfx.service.AiAnalysisService;
import com.gyb.xqfx.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final OllamaClient ollamaClient;
    private final AiPromptTemplateMapper templateMapper;
    private final AiCallLogMapper callLogMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "大模型服务健康检查")
    @GetMapping("/health")
    public Result<Boolean> health() {
        boolean ok = ollamaClient.isAvailable();
        return Result.success(ok ? Boolean.TRUE : Boolean.FALSE,
                ok ? "Ollama 服务可用" : "Ollama 服务不可用，将使用规则化降级");
    }

    @Operation(summary = "本机已安装模型列表")
    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.success(ollamaClient.listModels());
    }

    @Operation(summary = "生成学生学情分析报告")
    @PostMapping("/report")
    public Result<AiAnalysisService.AiResult> report(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId,
                                                     @RequestParam(required = false) Long userId) {
        return Result.success(aiAnalysisService.generateReport(studentId, termId, userId));
    }

    @Operation(summary = "生成个性化改进建议")
    @PostMapping("/advice")
    public Result<AiAnalysisService.AiResult> advice(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId,
                                                     @RequestParam(required = false) Long userId) {
        return Result.success(aiAnalysisService.generateAdvice(studentId, termId, userId));
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

    @Operation(summary = "提示词模板列表")
    @GetMapping("/templates")
    public Result<List<AiPromptTemplate>> templates() {
        return Result.success(templateMapper.selectList(null));
    }

    @Operation(summary = "新增或更新提示词模板")
    @PostMapping("/templates")
    public Result<Boolean> saveTemplate(@RequestBody AiPromptTemplate template) {
        return Result.success(template.getId() == null
                ? templateMapper.insert(template) > 0
                : templateMapper.updateById(template) > 0);
    }

    @Operation(summary = "知识库文档列表")
    @GetMapping("/knowledge")
    public Result<List<KnowledgeDoc>> knowledgeList() {
        return Result.success(knowledgeService.listDocs());
    }

    @Operation(summary = "录入知识库文档（自动分片与向量化）")
    @PostMapping(value = "/knowledge", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Result<Long> addKnowledge(@RequestParam String docName,
                                     @RequestParam(defaultValue = "OTHER") String docType,
                                     @RequestBody String content) {
        return Result.success(knowledgeService.addDoc(docName, docType, content), "文档已入库");
    }

    @Operation(summary = "删除知识库文档")
    @DeleteMapping("/knowledge/{id}")
    public Result<Boolean> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.success(true, "已删除");
    }

    @Operation(summary = "大模型调用日志")
    @GetMapping("/logs")
    public Result<List<AiCallLog>> logs() {
        return Result.success(callLogMapper.selectList(null));
    }
}
