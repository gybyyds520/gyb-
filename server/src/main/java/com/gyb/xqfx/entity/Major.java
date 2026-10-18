package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 专业
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("major")
public class Major extends BaseEntity {

    /** 所属学院 */
    private Long collegeId;

    /** 专业代码 */
    private String majorCode;

    /** 专业名称 */
    private String majorName;

    /** 学位类型 */
    private String degreeType;
}
