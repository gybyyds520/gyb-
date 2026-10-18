package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 机器学习模型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ml_model")
public class MlModel extends BaseEntity {

    /** 模型编码 */
    private String modelCode;

    /** 模型名称 */
    private String modelName;

    /** 算法：logistic_regression / random_forest / xgboost */
    private String algorithm;

    private String version;

    /** TRAINED / DEPLOYED / DISABLED */
    private String status;

    /** 评估指标 JSON */
    private String metricsJson;

    /** 特征列表 JSON */
    private String featureListJson;

    private String modelPath;

    private Integer trainSampleCount;

    private LocalDateTime trainTime;
}
