package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 课堂表现
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class_performance")
public class ClassPerformance extends BaseEntity {

    private Long studentId;

    private Long courseOfferingId;

    private Long termId;

    private LocalDate recordDate;

    /** 课堂参与度 0-10 */
    private BigDecimal participationScore;

    /** 作业得分 */
    private BigDecimal homeworkScore;

    /** 提问/互动次数 */
    private Integer questionCount;

    private String remark;
}
