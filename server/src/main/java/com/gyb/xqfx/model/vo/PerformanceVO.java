package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课堂表现汇总视图对象
 */
@Data
public class PerformanceVO {

    private Long studentId;
    private String studentNo;
    private String studentName;

    /** 记录条数 */
    private Integer recordCount;

    /** 平均参与度 0-10 */
    private BigDecimal avgParticipation;

    /** 平均作业得分 */
    private BigDecimal avgHomework;

    /** 互动总次数 */
    private Integer totalQuestions;

    /** 评级：积极 / 一般 / 待提升 */
    private String level;
}
