package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 数据质量汇总视图对象
 */
@Data
public class QualityReportVO {

    /** 报告条数 */
    private Integer reportCount;

    /** 问题总数 */
    private Integer problemCount;

    /** 参与校验的记录总数 */
    private Long totalRecords;

    /** 数据健康度（百分比） */
    private BigDecimal healthRate;

    /** 按问题类型分布 */
    private Map<String, Integer> byProblemType;

    /** 按数据集分布 */
    private Map<String, Integer> byDataset;
}
