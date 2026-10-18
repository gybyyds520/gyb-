package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预警规则
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("warning_rule")
public class WarningRule extends BaseEntity {

    private String ruleCode;

    private String ruleName;

    /** 规则维度类型：ACADEMIC学业/ATTENDANCE出勤/HOMEWORK作业/FAIL挂科/PROGRESS进度/PSYCH心理/BEHAVIOR行为/CONSUMPTION消费/COMPREHENSIVE综合 */
    private String warningType;

    /** 预警等级：1蓝 2黄 3橙 4红 */
    private Integer level;

    /** 触发条件 JSON */
    private String conditionJson;

    /** 权重 */
    private BigDecimal weight;

    /** 风险概率阈值，命中即触发该等级 */
    private BigDecimal scoreThreshold;

    private Integer enabled;

    private String description;
}
