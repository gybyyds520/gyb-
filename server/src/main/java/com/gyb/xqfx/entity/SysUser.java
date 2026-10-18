package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键 */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码(BCrypt)，不对外返回 */
    @TableField(select = false)
    private String password;

    /** 姓名 */
    private String realName;

    /** 类型: STUDENT/TEACHER/COUNSELOR/COLLEGE_ADMIN/SCHOOL_ADMIN/ADMIN/PARENT */
    private String userType;

    /** 关联学生ID（学生账号），用于学生端身份解析 */
    private Long studentId;

    /** 关联教师ID（教师/辅导员账号），用于教师端身份解析 */
    private Long teacherId;

    private String phone;

    private String email;

    private String avatar;

    /** 状态: 1启用 0禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
