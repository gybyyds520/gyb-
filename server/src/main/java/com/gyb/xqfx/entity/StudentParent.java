package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家长与学生绑定关系
 *
 * <p>家长账号通过本表关联到具体学生，一个家长可绑定多名子女，
 * 一名学生也可有多名监护人（父母），主监护人用于接收通知。</p>
 */
@Data
@TableName("student_parent")
public class StudentParent {

    /** 主键 */
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 家长登录账号ID（sys_user.id） */
    private Long parentUserId;

    /** 关系: FATHER父亲 / MOTHER母亲 / GUARDIAN监护人 */
    private String relation;

    private String parentName;

    /** 联系电话（脱敏） */
    private String parentPhone;

    /** 是否主监护人: 1是 0否 */
    private Integer isPrimary;

    /** 状态: 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDeleted;
}
