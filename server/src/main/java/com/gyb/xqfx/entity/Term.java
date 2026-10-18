package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学期
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("term")
public class Term extends BaseEntity {

    /** 学期代码，如 2026-2027-1 */
    private String termCode;

    /** 学期名称 */
    private String termName;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 是否当前学期 */
    private Integer isCurrent;
}
