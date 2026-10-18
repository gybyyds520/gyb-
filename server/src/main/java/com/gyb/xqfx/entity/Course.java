package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 课程
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course")
public class Course extends BaseEntity {

    /** 课程代码 */
    private String courseCode;

    /** 课程名称 */
    private String courseName;

    /** 学分 */
    private BigDecimal credit;

    /** PUBLIC公共课 / CORE核心课 / MAJOR专业课 / ELECTIVE选修 */
    private String courseType;

    /** 开课学院 */
    private Long collegeId;

    /** 总学时 */
    private Integer totalHours;
}
