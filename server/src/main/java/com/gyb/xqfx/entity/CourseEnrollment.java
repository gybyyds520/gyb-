package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 学生选课关系 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_enrollment")
public class CourseEnrollment extends BaseEntity {

    private Long courseOfferingId;

    private Long studentId;

    private LocalDateTime enrollTime;
}
