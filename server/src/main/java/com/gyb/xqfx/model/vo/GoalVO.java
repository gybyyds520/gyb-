package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标视图对象（含实时计算的当前值与差距）
 */
@Data
public class GoalVO {

    private Long id;

    private Long studentId;

    private String studentNo;

    private String studentName;

    private Long termId;

    private String goalType;

    private String goalTypeText;

    private BigDecimal targetValue;

    private Long courseId;

    private String courseName;

    private String status;

    private String statusText;

    private LocalDate deadline;

    private String note;

    /** 当前值（实时计算） */
    private BigDecimal currentValue;

    /** 差距 = 目标 - 当前 */
    private BigDecimal gap;

    /** 达成进度 0-100，已达成恒为 100 */
    private Integer progress;

    private LocalDateTime createTime;
}
