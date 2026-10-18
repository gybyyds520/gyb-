package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 干预工单视图对象
 */
@Data
public class InterventionTicketVO {

    private Long id;
    private String ticketNo;
    private Long studentId;
    private Long ownerUserId;
    private String interveneType;
    private String plan;
    private String status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate closeDate;
    private String conclusion;
    private String effectEval;

    private String studentNo;
    private String studentName;
    private Long logCount;
}
