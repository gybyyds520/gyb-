package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预测结果视图对象
 */
@Data
public class PredictionVO {

    private Long id;
    private Long studentId;
    private Long termId;
    private Long modelId;

    private BigDecimal predictedScore;
    private BigDecimal predictedGpa;

    /** 1蓝 2黄 3橙 4红 */
    private Integer riskLevel;

    private BigDecimal riskProbability;
    private String explainJson;
    private BigDecimal actualScore;

    private String studentNo;
    private String studentName;
    private String clazzName;
    private String termName;
}
