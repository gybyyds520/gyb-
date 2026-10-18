package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 试卷
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_paper")
public class ExamPaper extends BaseEntity {

    private Long courseOfferingId;

    private Long termId;

    private String paperName;

    private BigDecimal totalScore;

    private BigDecimal avgScore;

    private BigDecimal maxScore;

    private BigDecimal minScore;

    private BigDecimal stdDev;

    /** 及格率（百分比） */
    private BigDecimal passRate;

    /** 优秀率（百分比） */
    private BigDecimal excellentRate;

    /** 难度系数：平均得分率，越大越容易 */
    private BigDecimal difficulty;

    /** 区分度 */
    private BigDecimal discrimination;
}
