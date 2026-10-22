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
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 外部云端大模型客户端（OpenAI 兼容接口）。
 *
 * <p>定位：本地 Ollama 不可用时的可选降级通道（双轨策略：本地优先、云端兜底）。
 * 出校提示词默认脱敏（手机号/身份证），未配置 api-key 时全部方法直接返回不可用，
 * 行为与未接入云端完全一致（向后兼容）。</p>
 */
@Slf4j
@Component
public class RemoteLlmClient {

    private static final Pattern PHONE_MASK = Pattern.compile("(?<![0-9])(1[3-9]\\d{9})(?![0-9])");
    private static final Pattern ID_CARD_MASK = Pattern.compile("(?<![0-9])(\\d{17}[0-9Xx])(?![0-9])");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final boolean maskPrompt;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 连通性探测缓存（60 秒 TTL，避免每次健康检查都发起真实请求） */
    private static final long PROBE_TTL_MS = 60_000L;
    private volatile boolean probeAvailable = false;
    private volatile long probeAtMs = 0L;
    private volatile String probeError = "";

    public RemoteLlmClient(@Value("${xqfx.llm.cloud.base-url:}") String baseUrl,
                           @Value("${xqfx.llm.cloud.api-key:}") String apiKey,
                           @Value("${xqfx.llm.cloud.model:gpt-4o-mini}") String model,
                           @Value("${xqfx.llm.cloud.timeout:60s}") String timeout,
                           @Value("${xqfx.privacy.mask-llm-prompt:true}") boolean maskPrompt,
                           ObjectMapper objectMapper) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maskPrompt = maskPrompt;
        this.timeout = parseDuration(timeout);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** 是否配置了云端通道（未配置则所有调用返回不可用） */
    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty();
    }

    /**
     * 云端连通性探测（GET /models，60 秒 TTL 缓存）。
     * <p>仅当配置了 api-key 时才探测；未配置或探测失败返回 false。
     * 探测请求不携带业务数据、开销极小，用于健康检查真实反映云端通道可用性。</p>
     */
    public boolean isAvailable() {
        if (!isConfigured()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - probeAtMs < PROBE_TTL_MS) {
            return probeAvailable;
        }
        probeAtMs = now;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() == 200;
            probeAvailable = ok;
            probeError = ok ? "" : "HTTP " + resp.statusCode();
            return ok;
        } catch (Exception e) {
            probeAvailable = false;
            probeError = e.getMessage();
            return false;
        }
    }

    /** 最近一次探测是否可用（供健康检查展示缓存结果） */
    public boolean lastProbeResult() {
        return probeAvailable;
    }

    /** 最近一次探测的错误信息（无错误时为空字符串） */
    public String probeError() {
        return probeError;
    }

    /**
     * 非流式对话（OpenAI 兼容 /chat/completions）。
     *
     * @return 失败或未配置时返回 null（与 OllamaClient.chat 契约一致，复用其 ChatResult 结构）
     */
    public OllamaClient.ChatResult chat(String system, String prompt) {
        if (!isConfigured()) {
            return null;
        }
        try {
            ObjectNode body = buildBody(system, prompt, false);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("cloud llm chat rejected: status={}, body={}", resp.statusCode(), truncate(resp.body()));
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isEmpty()) {
                return null;
            }
            JsonNode usage = root.path("usage");
            OllamaClient.ChatResult r = new OllamaClient.ChatResult();
            r.model = "cloud:" + model;
            r.content = content;
            r.promptTokens = usage.path("prompt_tokens").asInt(0);
            r.completionTokens = usage.path("completion_tokens").asInt(0);
            return r;
        } catch (Exception e) {
            log.warn("cloud llm chat failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式对话（SSE），文本片段通过 onChunk 回调下发。
     *
     * @return 是否成功启用云端流式回复
     */
    public boolean chatStream(String system, String prompt, Consumer<String> onChunk) {
        if (!isConfigured()) {
            return false;
        }
        try {
            ObjectNode body = buildBody(system, prompt, true);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.warn("cloud llm stream rejected: status={}", resp.statusCode());
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                        if (!delta.isEmpty()) {
                            onChunk.accept(delta);
                        }
                    } catch (Exception ignored) {
                        // 忽略单条无效 SSE 数据
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("cloud llm stream failed: {}", e.getMessage());
            return false;
        }
    }

    private ObjectNode buildBody(String system, String prompt, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", sanitize(system));
        messages.addObject().put("role", "user").put("content", sanitize(prompt));
        body.put("stream", stream);
        return body;
    }

    /** 出校前脱敏：手机号保留前 3 后 4、身份证保留前 3 后 3（带数字边界防误伤） */
    private String sanitize(String text) {
        if (!maskPrompt || text == null || text.isEmpty()) {
            return text;
        }
        String s = PHONE_MASK.matcher(text).replaceAll(mr -> {
            String n = mr.group(1);
            return n.substring(0, 3) + "****" + n.substring(7);
        });
        s = ID_CARD_MASK.matcher(s).replaceAll(mr -> {
            String n = mr.group(1);
            return n.substring(0, 3) + "********" + n.substring(15);
        });
        return s;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    private static Duration parseDuration(String raw) {
        try {
            String v = raw.trim();
            if (v.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2)));
            }
            if (v.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(v.substring(0, v.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(v));
        } catch (Exception e) {
            return Duration.ofSeconds(60);
        }
    }
}
