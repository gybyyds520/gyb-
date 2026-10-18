package com.gyb.xqfx.model.ml;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * ML 服务请求/响应 DTO
 *
 * <p>统一 snake_case 命名，与 Python 侧字段保持一致。</p>
 */
public class MlDtos {

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class TrainRequest {
        private String modelType;
        private List<MlFeatureRow> dataset;
        private Double testSize;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class TrainResponse {
        private String modelType;
        private String version;
        private Integer sampleCount;
        private Integer featureCount;
        private Map<String, Object> metrics;
        private Map<String, Object> featureImportance;
        private String modelPath;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PredictRequest {
        private String modelType;
        private List<MlFeatureRow> rows;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PredictItem {
        private Long studentId;
        private Long termId;
        private Integer riskLabel;
        private Double riskProbability;
        private String riskLevel;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PredictResponse {
        private String modelType;
        private String version;
        private List<PredictItem> predictions;
    }

    @Data
    public static class HealthResponse {
        private String status;
        private List<String> models;
    }
}
