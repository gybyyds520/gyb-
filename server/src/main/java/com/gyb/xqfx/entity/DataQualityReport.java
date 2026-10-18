package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据质量报告
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_quality_report")
public class DataQualityReport extends BaseEntity {

    private Long taskId;

    /** 数据集：STUDENT / SCORE */
    private String dataset;

    /** 校验规则 */
    private String checkRule;

    /** MISSING缺失 / OUT_OF_RANGE越界 / DUPLICATE重复 / INCONSISTENT不一致 */
    private String problemType;

    private Integer problemCount;

    /** 问题样例（JSON 数组） */
    private String sampleJson;

    private LocalDateTime checkTime;
}
