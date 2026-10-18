package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 学生视图对象（含组织名称与脱敏字段）
 */
@Data
public class StudentVO {

    private Long id;
    private String studentNo;
    private String name;
    private String gender;
    private LocalDate birthday;
    private String phone;
    private String email;
    private Long clazzId;
    private Long majorId;
    private Long collegeId;
    private String grade;
    private LocalDate enrollDate;
    private String politicalStatus;
    private String dormitory;
    private String status;

    /** 班级名称 */
    private String clazzName;
    /** 专业名称 */
    private String majorName;
    /** 学院名称 */
    private String collegeName;

    /** 脱敏后的手机号 */
    private String phoneMasked;
    /** 是否已脱敏 */
    private Boolean masked = false;
}
