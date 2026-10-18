package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 试卷题目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_question")
public class ExamQuestion extends BaseEntity {

    private Long examPaperId;

    private String questionNo;

    /** SINGLE单选 / MULTI多选 / JUDGE判断 / FILL填空 / SHORT简答 / CALC计算 */
    private String questionType;

    /** 考查知识点 */
    private String knowledgePoint;

    /** 题干：本地大模型生成同类题的依据 */
    private String stem;

    /** 选项 JSON 数组，如 ["A. ...","B. ..."]；填空/简答/计算题为空 */
    private String optionsJson;

    /** 参考答案 */
    private String answer;

    /** 试题解析 */
    private String analysis;

    private BigDecimal fullScore;

    private BigDecimal avgScore;

    /** 得分率（百分比） */
    private BigDecimal scoreRate;

    /** 难度系数：平均得分率，越大越容易 */
    private BigDecimal difficulty;

    /** 区分度：高分组得分率 - 低分组得分率 */
    private BigDecimal discrimination;

    private BigDecimal stdDev;
}
