package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师布置的作业（作业帮式作业闭环）
 *
 * <p>题目以 JSON 数组存储：[{no,type,content,answer,score}]，
 * type 支持 CHOICE单选 / FILL填空 / ESSAY简答作文（AI 批改）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("homework")
public class Homework extends BaseEntity {

    private String title;

    /** 课程ID */
    private Long courseId;

    /** 归属教学班（章节测验） */
    private Long courseOfferingId;

    private String courseName;

    /** 班级ID */
    private Long clazzId;

    /** 布置教师 sys_user.id */
    private Long creatorId;

    /** 题目 JSON 数组 */
    private String questionJson;

    private BigDecimal totalScore;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** ACTIVE进行中 / CLOSED已截止 */
    private String status;
}
