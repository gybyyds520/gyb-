package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 班级
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("clazz")
public class Clazz extends BaseEntity {

    private Long majorId;

    /** 班级代码 */
    private String clazzCode;

    /** 班级名称 */
    private String clazzName;

    /** 年级 */
    private String grade;

    /** 班主任(教师ID) */
    private Long headTeacherId;

    /** 辅导员(教师ID) */
    private Long counselorId;

    /** 学生人数 */
    private Integer studentCount;
}
