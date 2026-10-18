package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签定义
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tag_definition")
public class TagDefinition extends BaseEntity {

    private String tagCode;

    private String tagName;

    /** ACADEMIC / BEHAVIOR / RISK / POTENTIAL */
    private String tagCategory;

    /**
     * 自动打标规则 JSON，如 {"avg_score":{"lt":60}}
     * 支持操作符 gte / gt / lte / lt / eq
     */
    private String ruleJson;

    private String color;

    /** 1 自动标签 / 0 手工标签 */
    private Integer isAuto;

    private String description;
}
