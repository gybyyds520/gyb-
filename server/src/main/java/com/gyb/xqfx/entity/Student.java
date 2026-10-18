package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学生
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student")
public class Student extends BaseEntity {

    /** 学号 */
    private String studentNo;

    private String name;

    private String gender;

    private LocalDate birthday;

    /** 身份证号（脱敏存储） */
    private String idCardNo;

    /** 手机号（脱敏存储） */
    private String phone;

    private String email;

    private Long clazzId;

    private Long majorId;

    private Long collegeId;

    /** 年级 */
    private String grade;

    /** 入学日期 */
    private LocalDate enrollDate;

    /** 政治面貌 */
    private String politicalStatus;

    /** 宿舍 */
    private String dormitory;

    /** ENROLLED在读 / SUSPEND休学 / GRADUATED毕业 / DROPPED退学 */
    private String status;

    /** 关联登录账号 */
    private Long userId;
}
