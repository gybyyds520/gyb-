package com.gyb.xqfx.model.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩 Excel 导入模型
 */
@Data
public class ScoreExcel {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("课程代码")
    private String courseCode;

    @ExcelProperty("学期代码")
    private String termCode;

    @ExcelProperty("平时成绩")
    private BigDecimal usualScore;

    @ExcelProperty("期中成绩")
    private BigDecimal midScore;

    @ExcelProperty("期末成绩")
    private BigDecimal finalScore;

    @ExcelProperty("总评成绩")
    private BigDecimal totalScore;

    /** NORMAL/DEFER/ABSENT/CHEAT */
    @ExcelProperty("考试状态")
    private String examStatus;
}
