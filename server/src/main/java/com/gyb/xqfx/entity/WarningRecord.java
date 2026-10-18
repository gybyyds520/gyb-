package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学业预警记录
 *
 * <p>说明：表 warning_record 在 Phase 0 已建，等级列名为 {@code level}、
 * 责任人名 {@code assigned_to}，此处通过 @TableField 做字段映射。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("warning_record")
public class WarningRecord extends BaseEntity {

    private String warningNo;

    private Long studentId;

    private Long termId;

    private Long ruleId;

    private Long predictionId;

    /** 预警等级：1蓝 2黄 3橙 4红（对应列 level） */
    @TableField("level")
    private Integer warningLevel;

    /** ACADEMIC / ATTENDANCE / BEHAVIOR / COMPREHENSIVE */
    private String warningType;

    /** 预警标题 */
    private String title;

    /** 预警内容 */
    private String content;

    /** 触发值（数值型指标） */
    @TableField("trigger_value")
    private BigDecimal triggerValue;

    private String triggerDetail;

    private BigDecimal riskProbability;

    /** ACTIVE / HANDLED / CLOSED / SUPPRESSED */
    private String status;

    /** 责任人（对应列 assigned_to） */
    @TableField("assigned_to")
    private Long ownerUserId;

    /** 处理时间（对应列 assigned_time） */
    @TableField("assigned_time")
    private LocalDateTime handleTime;

    /** 处理结果（对应列 resolve_remark） */
    @TableField("resolve_remark")
    private String handleResult;
}
