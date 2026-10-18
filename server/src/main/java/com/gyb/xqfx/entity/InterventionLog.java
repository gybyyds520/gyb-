package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 干预过程记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intervention_log")
public class InterventionLog extends BaseEntity {

    private Long ticketId;

    /** CREATE / ASSIGN / ACTION / EVALUATE / CLOSE */
    private String actionType;

    private String actionContent;

    private LocalDate actionDate;

    private Long operatorId;

    private String effectEval;

    private String attachmentUrl;
}
