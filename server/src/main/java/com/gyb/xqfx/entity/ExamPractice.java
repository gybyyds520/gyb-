package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 错题举一反三巩固练习
 *
 * <p>由本地大模型围绕学生做错的题目生成同类练习题，用于巩固薄弱知识点。
 * 生成结果落库，教师可回看与复用，学生可在「我的练习」中查看自己的练习。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_practice")
public class ExamPractice extends BaseEntity {

    private Long examPaperId;

    /** 来源错题（题目ID） */
    private Long examQuestionId;

    /** 目标学生，为空表示面向全班 */
    private Long studentId;

    private String courseName;

    private String knowledgePoint;

    private String questionType;

    /** AI 本地模型 / RULE 规则降级 */
    private String sourceType;

    private String modelName;

    private Integer questionCount;

    /** 练习题 JSON 数组 */
    private String contentJson;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer costMs;

    /** SUCCESS / FAILED / FALLBACK */
    private String status;

    private String errorMsg;

    /** 生成人 */
    private Long userId;
}
