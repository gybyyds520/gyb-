package com.gyb.xqfx.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Stable Diffusion 图像服务客户端（独立进程，默认端口 8001）
 *
 * <p>调用 sd-turbo 生成激励卡背景图，返回 PNG 字节数组。
 * 服务不可用时抛出 BusinessException，由上层给出友好提示。</p>
 */
@Slf4j
@Component
public class SdServiceClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SdServiceClient(@Value("${xqfx.sd.base-url:http://127.0.0.1:8001}") String baseUrl,
                           ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** SD 服务是否可用 */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            return "UP".equals(node.path("status").asText());
        } catch (Exception e) {
            log.warn("SD 图像服务不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 生成激励卡背景图，返回 PNG 字节 */
    public byte[] generate(String prompt, int width, int height) {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        body.put("width", width);
        body.put("height", height);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/sd/generate"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BusinessException("图像服务返回 " + status + "：" + truncate(response.body()));
            }
            JsonNode node = objectMapper.readTree(response.body());
            String base64 = node.path("image_base64").asText();
            if (base64.isBlank()) {
                throw new BusinessException("图像服务返回为空");
            }
            return Base64.getDecoder().decode(base64);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调用图像服务失败：" + e.getMessage());
        }
    }

    private String truncate(String s) {
        return s == null ? "" : (s.length() > 120 ? s.substring(0, 120) : s);
    }
}
