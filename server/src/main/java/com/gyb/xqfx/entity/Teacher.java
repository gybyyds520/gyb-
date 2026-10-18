package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教师
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teacher")
public class Teacher extends BaseEntity {

    /** 工号 */
    private String teacherNo;

    private String name;

    private String gender;

    private Long collegeId;

    /** 职称 */
    private String title;

    /** 学位学历 */
    private String degree;

    /** 研究方向 */
    private String researchArea;

    private String phone;

    private String email;

    /** 关联登录账号 */
    private Long userId;
}
