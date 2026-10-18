package com.gyb.xqfx.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据质量问题明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityIssue {

    /** 数据集：STUDENT / SCORE */
    private String dataset;

    /** 校验规则 */
    private String checkRule;

    /** MISSING缺失 / OUT_OF_RANGE越界 / DUPLICATE重复 / INCONSISTENT不一致 */
    private String problemType;

    /** Excel 行号（从 2 开始，1 为表头） */
    private int rowIndex;

    /** 问题描述 */
    private String message;
}
