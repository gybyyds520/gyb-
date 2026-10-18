package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 学生小题得分
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_question_score")
public class ExamQuestionScore extends BaseEntity {

    private Long examPaperId;

    private Long examQuestionId;

    private Long studentId;

    private BigDecimal score;
}
