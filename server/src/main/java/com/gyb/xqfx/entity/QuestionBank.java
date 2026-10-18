package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 题库（试卷录入 AI 解析出的题目，独立于考试卷，可复用进作业/练习）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_bank")
public class QuestionBank extends BaseEntity {

    /** 来源录入批次 paper_scan.id */
    private Long scanId;

    private Long courseId;

    private String courseName;

    /** 题号 */
    private String questionNo;

    /** 题型 SINGLE/MULTI/FILL/JUDGE/ESSAY */
    private String questionType;

    /** 题干 */
    private String stem;

    /** 选项 JSON 数组 */
    private String optionsJson;

    /** 参考答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 分值 */
    private BigDecimal score;

    /** 知识点 */
    private String knowledgePoint;

    /** 易/中/难 */
    private String difficulty;

    /** ACTIVE / DISABLED */
    private String status;
}
