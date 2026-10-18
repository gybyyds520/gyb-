package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模型评估对比视图
 */
@Data
public class ModelCompareVO {

    private Long modelId;
    private String modelCode;
    private String modelName;
    private String algorithm;
    private String version;
    /** TRAINED / DEPLOYED / DISABLED */
    private String status;
    private LocalDateTime trainTime;
    private Integer trainSampleCount;
    /** 评估指标（accuracy/precision/recall/f1/auc/rmse） */
    private Map<String, Object> metrics;
}
