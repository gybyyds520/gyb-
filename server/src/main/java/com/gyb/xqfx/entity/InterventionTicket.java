package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 干预工单（预警闭环）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intervention_ticket")
public class InterventionTicket extends BaseEntity {

    private String ticketNo;

    /** 来源预警ID */
    private Long warningId;

    private Long studentId;

    /** 帮扶责任人 */
    private Long ownerUserId;

    /** TALK谈心 / TUTORING辅导 / HOMEWORK作业督导 / PSYCHOLOGY心理 / FAMILY家校联动 */
    private String interveneType;

    private String plan;

    /** OPEN / PROCESSING / CLOSED */
    private String status;

    private LocalDate startDate;

    private LocalDate dueDate;

    private LocalDate closeDate;

    private String conclusion;

    /** IMPROVED明显改善 / SLIGHT好转 / NONE无明显变化 / WORSE恶化 */
    private String effectEval;

    /** 承诺处理时限（小时），按预警等级设定 */
    private Integer slaHours;

    /** 超时时长（小时），未超时为 0 */
    private Integer overdueHours;

    /** 升级层级：0 未升级 / 1 院系管理员 / 2 校级学工负责人 */
    private Integer escalateLevel;

    /** 预警有效性反馈：EFFECTIVE有效 / FALSE_POSITIVE误报 / INCOMPLETE信息不全 / ADJUST建议调整 */
    private String feedback;

    private String feedbackNote;

    /** 最近催办时间 */
    private java.time.LocalDateTime lastRemindTime;
}
