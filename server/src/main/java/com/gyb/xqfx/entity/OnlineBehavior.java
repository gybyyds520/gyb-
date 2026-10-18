package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 在线学习行为
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_behavior")
public class OnlineBehavior extends BaseEntity {

    private Long studentId;

    private Long courseId;

    private Long termId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 登录次数 */
    private Integer loginCount;

    /** 在线学习时长（分钟） */
    private Integer studyMinutes;

    /** 视频完成度(%) */
    private BigDecimal videoProgress;

    /** 资源查看次数 */
    private Integer resourceViewCount;

    /** 论坛发帖数 */
    private Integer forumPostCount;

    /** 作业提交次数 */
    private Integer assignmentSubmitCount;
}
