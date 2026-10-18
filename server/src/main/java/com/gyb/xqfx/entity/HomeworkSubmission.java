package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生作业提交
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("homework_submission")
public class HomeworkSubmission extends BaseEntity {

    private Long homeworkId;

    private Long studentId;

    /** 答案 JSON [{no,answer}] */
    private String answersJson;

    /** 总得分 */
    private BigDecimal score;

    /** 客观题自动得分 */
    private BigDecimal autoScore;

    /** SUBMITTED已提交 / AUTO_GRADED客观题已批 / GRADED已批 */
    private String status;

    private LocalDateTime submitTime;

    private LocalDateTime gradeTime;

    /** 批改反馈（AI 或教师） */
    private String feedback;
}
