package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学生标签
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_tag")
public class StudentTag extends BaseEntity {

    private Long studentId;

    private Long tagId;

    private Long termId;

    private String tagValue;

    /** AUTO 自动 / MANUAL 手工 */
    private String source;

    private LocalDate effectiveDate;

    private LocalDate expireDate;
}
