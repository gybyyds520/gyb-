package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预测结果
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prediction_result")
public class PredictionResult extends BaseEntity {

    private Long studentId;

    private Long courseId;

    private Long termId;

    private Long modelId;

    /** 预测成绩 */
    private BigDecimal predictedScore;

    /** 预测绩点 */
    private BigDecimal predictedGpa;

    /** 风险等级：1蓝 2黄 3橙 4红 */
    private Integer riskLevel;

    /** 风险概率 */
    private BigDecimal riskProbability;

    /** 可解释性说明 JSON（特征贡献） */
    private String explainJson;

    /** 实际成绩（事后回填，用于模型评估） */
    private BigDecimal actualScore;
}
