package com.gyb.xqfx.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ollama 本地大模型客户端
 *
 * <p>说明：Ollama 的 HTTP 服务基于 h11，不支持明文 HTTP/2 升级，
 * 因此强制使用 HTTP/1.1，否则会出现 "Unsupported upgrade request"。</p>
 */
@Slf4j
@Component
public class OllamaClient {

    private final String baseUrl;
    private final String primaryModel;
    private final String fallbackModel;
    private final String embedModel;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(@Value("${xqfx.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
                        @Value("${xqfx.ollama.primary-model:llama3.2}") String primaryModel,
                        @Value("${xqfx.ollama.fallback-model:qwen2.5}") String fallbackModel,
                        @Value("${xqfx.ollama.embed-model:nomic-embed-text}") String embedModel,
                        @Value("${xqfx.ollama.timeout:120s}") String timeout,
                        ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.embedModel = embedModel;
        this.timeout = parseDuration(timeout);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** 服务可用性 */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Ollama 服务不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 已安装模型列表 */
    public List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(res.body());
            List<String> names = new ArrayList<>();
            JsonNode models = root.get("models");
            if (models != null && models.isArray()) {
                models.forEach(n -> names.add(n.path("name").asText()));
            }
            return names;
        } catch (Exception e) {
            log.warn("获取模型列表失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 非流式对话，失败时自动尝试备用模型
     *
     * @return 返回内容为 null 表示调用失败
     */
    public ChatResult chat(String systemPrompt, String userPrompt) {
        ChatResult result = doChat(primaryModel, systemPrompt, userPrompt);
        if (result == null && fallbackModel != null && !fallbackModel.equals(primaryModel)) {
            log.info("主模型 {} 调用失败，尝试备用模型 {}", primaryModel, fallbackModel);
            result = doChat(fallbackModel, systemPrompt, userPrompt);
        }
        return result;
    }

    /**
     * 使用指定模型对话（不同场景可选用不同规模的本地模型），
     * 指定模型不可用时回落到主模型与备用模型。
     *
     * @param preferred 期望的模型名，为空则等同于 {@link #chat}
     */
    public ChatResult chatWithModel(String preferred, String systemPrompt, String userPrompt) {
        if (preferred == null || preferred.isBlank()) {
            return chat(systemPrompt, userPrompt);
        }
        ChatResult result = doChat(preferred, systemPrompt, userPrompt);
        if (result != null) {
            return result;
        }
        log.info("指定模型 {} 不可用，回落主模型", preferred);
        return chat(systemPrompt, userPrompt);
    }

    /** 当前配置的主模型名（健康检查展示用） */
    public String primaryModelName() {
        return primaryModel;
    }

    /**
     * 解析实际可用的模型名：配置的模型名可能缺少标签（如 llama3.2），
     * 此处在本地已安装模型中做精确匹配、前缀匹配与包含匹配。
     */
    public String resolveModel(String preferred) {
        List<String> models = listModels();
        if (models.isEmpty() || preferred == null) {
            return preferred;
        }
        if (models.contains(preferred)) {
            return preferred;
        }
        return models.stream()
                .filter(m -> m.startsWith(preferred + ":"))
                .findFirst()
                .orElseGet(() -> models.stream()
                        .filter(m -> m.contains(preferred))
                        .findFirst()
                        .orElse(null));
    }

    private ChatResult doChat(String model, String systemPrompt, String userPrompt) {
        try {
            String actualModel = resolveModel(model);
            if (actualModel == null) {
                log.warn("本地未安装模型：{}", model);
                return null;
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", actualModel);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt));
            }
            messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userPrompt));

            String raw = post("/api/chat", objectMapper.writeValueAsString(body));
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("message").path("content").asText(null);
            if (content == null) {
                return null;
            }
            ChatResult r = new ChatResult();
            r.model = actualModel;
            r.content = content;
            r.promptTokens = root.path("prompt_eval_count").asInt(0);
            r.completionTokens = root.path("eval_count").asInt(0);
            return r;
        } catch (Exception e) {
            log.warn("Ollama 对话失败（model={}）：{}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 流式对话，逐段回调（用于 SSE 打字机效果）
     *
     * @return 是否成功建立流式连接
     */
    public boolean chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", primaryModel);
            body.put("stream", true);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt));
            }
            messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userPrompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> res = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                return false;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    String content = node.path("message").path("content").asText(null);
                    if (content != null && !content.isEmpty()) {
                        onChunk.accept(content);
                    }
                    if (node.path("done").asBoolean(false)) {
                        break;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Ollama 流式对话失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 文本向量化（用于 RAG 检索）
     *
     * @return 向量；失败返回 null
     */
    public List<Double> embed(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", embedModel);
            body.put("prompt", text);
            String raw = post("/api/embeddings", objectMapper.writeValueAsString(body));
            JsonNode arr = objectMapper.readTree(raw).get("embedding");
            if (arr == null || !arr.isArray()) {
                return null;
            }
            List<Double> vec = new ArrayList<>(arr.size());
            arr.forEach(n -> vec.add(n.asDouble()));
            return vec;
        } catch (Exception e) {
            log.warn("文本向量化失败：{}", e.getMessage());
            return null;
        }
    }

    private String post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException(res.statusCode() + " " + res.body());
        }
        return res.body();
    }

    private static Duration parseDuration(String text) {
        if (text.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
        }
        if (text.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
        }
        return Duration.ofSeconds(120);
    }

    /** 对话结果 */
    public static class ChatResult {
        public String model;
        public String content;
        public int promptTokens;
        public int completionTokens;

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
