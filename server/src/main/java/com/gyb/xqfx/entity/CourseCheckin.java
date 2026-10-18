package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 课堂签到活动 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_checkin")
public class CourseCheckin extends BaseEntity {

    private Long courseOfferingId;

    private Long teacherId;

    private String code;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** ACTIVE / CLOSED */
    private String status = "ACTIVE";
}
