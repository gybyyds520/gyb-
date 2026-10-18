package com.gyb.xqfx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.model.ml.MlDtos;
import com.gyb.xqfx.model.ml.MlFeatureRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Python 预测服务客户端（基于 JDK 原生 HttpClient）
 *
 * <p>请求体由 Jackson 序列化为 snake_case JSON 后显式发送，
 * 避免不同 HTTP 客户端实现差异导致请求体丢失。</p>
 */
@Slf4j
@Component
public class MlServiceClient {

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MlServiceClient(@Value("${xqfx.ml.base-url:http://127.0.0.1:8000}") String baseUrl,
                           ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // 明文 HTTP/2（h2c）会先发升级请求，uvicorn 的 h11 无法解析，
                // 会出现 "Unsupported upgrade request" 并导致请求体丢失
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** 健康检查 */
    public boolean isAvailable() {
        try {
            String body = get("/health");
            MlDtos.HealthResponse res = objectMapper.readValue(body, MlDtos.HealthResponse.class);
            return res != null && "UP".equals(res.getStatus());
        } catch (Exception e) {
            log.warn("ML 服务不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 训练模型 */
    public MlDtos.TrainResponse train(String modelType, List<MlFeatureRow> dataset, double testSize) {
        MlDtos.TrainRequest req = new MlDtos.TrainRequest();
        req.setModelType(modelType);
        req.setDataset(dataset);
        req.setTestSize(testSize);
        try {
            String body = post("/ml/train", toJson(req));
            return objectMapper.readValue(body, MlDtos.TrainResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调用预测服务训练失败：" + e.getMessage());
        }
    }

    /** 批量预测 */
    public List<MlDtos.PredictItem> predict(String modelType, List<MlFeatureRow> rows) {
        MlDtos.PredictRequest req = new MlDtos.PredictRequest();
        req.setModelType(modelType);
        req.setRows(rows);
        try {
            String body = post("/ml/predict", toJson(req));
            MlDtos.PredictResponse res = objectMapper.readValue(body, MlDtos.PredictResponse.class);
            return res == null || res.getPredictions() == null ? List.of() : res.getPredictions();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调用预测服务推理失败：" + e.getMessage());
        }
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return send(request);
    }

    private String post(String path, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return send(request);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("请求预测服务失败：" + e.getMessage());
        }
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new BusinessException(status + " " + response.body());
        }
        return response.body();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("请求体序列化失败：" + e.getMessage());
        }
    }
}
