package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开课 / 教学班
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_offering")
public class CourseOffering extends BaseEntity {

    private Long termId;

    private Long courseId;

    /** 任课教师 */
    private Long teacherId;

    /** 行政班，跨班选修为空 */
    private Long clazzId;

    private Integer capacity;

    private String classroom;

    /** 上课时间 */
    private String schedule;
}
