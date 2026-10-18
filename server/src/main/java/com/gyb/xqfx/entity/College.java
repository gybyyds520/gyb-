package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学院
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("college")
public class College extends BaseEntity {

    /** 学院代码 */
    private String collegeCode;

    /** 学院名称 */
    private String collegeName;

    /** 负责人 */
    private String dean;

    private String phone;
}
