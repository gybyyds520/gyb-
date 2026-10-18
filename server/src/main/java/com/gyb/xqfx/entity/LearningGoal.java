package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学习目标
 *
 * <p>学生端自我设定：目标类型支持 均分(AVG_SCORE) / 绩点(GPA) / 单科成绩(COURSE)，
 * 教师端可查看与协助调整。当前值与差距由 GoalService 按成绩实时计算。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_goal")
public class LearningGoal extends BaseEntity {

    private Long studentId;

    /** 学期，为空表示累计目标 */
    private Long termId;

    /** AVG_SCORE 均分 / GPA 绩点 / COURSE 单科 */
    private String goalType;

    /** 目标值 */
    private BigDecimal targetValue;

    /** 单科目标时指定课程 */
    private Long courseId;

    /** ACTIVE 进行中 / ACHIEVED 已达成 / ABANDONED 已放弃 */
    private String status;

    /** 截止日期 */
    private LocalDate deadline;

    /** 目标说明 */
    private String note;
}
