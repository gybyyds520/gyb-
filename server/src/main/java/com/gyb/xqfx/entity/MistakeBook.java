package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生错题本
 *
 * <p>错题自动收录（考试/作业错题）与手动添加，按知识点/错因统计，
 * 学生可标记掌握并生成同类巩固练习（举一反三）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mistake_book")
public class MistakeBook extends BaseEntity {

    /** 学生ID */
    private Long studentId;

    /** 课程ID */
    private Long courseId;

    /** 课程名 */
    private String courseName;

    /** 知识点 */
    private String knowledgePoint;

    /** 题型: CHOICE/FILL/ESSAY/CALC/OTHER */
    private String questionType;

    /** 题目内容 */
    private String questionContent;

    /** 错误答案 */
    private String wrongAnswer;

    /** 正确答案 */
    private String rightAnswer;

    /** 来源: EXAM考试/HOMEWORK作业/MANUAL手动 */
    private String sourceType;

    /** 来源ID（考试/作业） */
    private Long sourceId;

    /** 错因: 概念不清/粗心/方法不当/未掌握 */
    private String mistakeReason;

    /** UNRESOLVED未掌握 / MASTERED已掌握 */
    private String status;
}
