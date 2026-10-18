package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 考勤记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attendance")
public class Attendance extends BaseEntity {

    private Long studentId;

    private Long courseOfferingId;

    private Long termId;

    /** 考勤日期 */
    private LocalDate checkDate;

    /** 节次 */
    private String period;

    /** PRESENT出勤 / LATE迟到 / EARLY早退 / LEAVE请假 / ABSENT缺勤 */
    private String status;

    private String remark;
}
