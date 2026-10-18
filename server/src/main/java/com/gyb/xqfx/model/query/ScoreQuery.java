package com.gyb.xqfx.model.query;

import lombok.Data;

/**
 * 成绩分页查询条件
 */
@Data
public class ScoreQuery {

    private String keyword;

    private Long studentId;

    private Long courseId;

    private Long termId;

    private Long clazzId;

    /** 仅看不及格 */
    private Boolean onlyFailed;
}
