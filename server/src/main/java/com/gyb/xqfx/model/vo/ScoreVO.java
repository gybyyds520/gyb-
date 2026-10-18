package com.gyb.xqfx.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩视图对象
 */
@Data
public class ScoreVO {

    private Long id;
    private Long studentId;
    private Long courseOfferingId;
    private Long courseId;
    private Long termId;

    private BigDecimal usualScore;
    private BigDecimal midScore;
    private BigDecimal finalScore;
    private BigDecimal totalScore;
    private BigDecimal gradePoint;
    private String examStatus;
    private Integer isPass;
    private Integer isRetake;

    private String studentNo;
    private String studentName;
    private String clazzName;
    private String courseName;
    private String courseCode;
    private String termName;
}
