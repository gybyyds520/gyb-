package com.gyb.xqfx.model.query;

import lombok.Data;

/**
 * 学生分页查询条件
 */
@Data
public class StudentQuery {

    /** 关键字：学号或姓名模糊匹配 */
    private String keyword;

    private Long collegeId;

    private Long majorId;

    private Long clazzId;

    /** 年级 */
    private String grade;

    /** 学籍状态 */
    private String status;
}
