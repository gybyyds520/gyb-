package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 成绩
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("score")
public class Score extends BaseEntity {

    private Long studentId;

    private Long courseOfferingId;

    private Long courseId;

    private Long termId;

    /** 平时成绩 */
    private BigDecimal usualScore;

    /** 期中成绩 */
    private BigDecimal midScore;

    /** 期末成绩 */
    private BigDecimal finalScore;

    /** 总评成绩 */
    private BigDecimal totalScore;

    /** 绩点 */
    private BigDecimal gradePoint;

    /** NORMAL正常 / DEFER缓考 / ABSENT缺考 / CHEAT作弊 */
    private String examStatus;

    /** 是否及格 */
    private Integer isPass;

    /** 是否重修 */
    private Integer isRetake;
}
