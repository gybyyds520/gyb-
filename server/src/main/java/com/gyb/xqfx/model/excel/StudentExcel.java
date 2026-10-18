package com.gyb.xqfx.model.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 学生 Excel 导入模型
 */
@Data
public class StudentExcel {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("性别")
    private String gender;

    @ExcelProperty("学院代码")
    private String collegeCode;

    @ExcelProperty("专业代码")
    private String majorCode;

    @ExcelProperty("班级代码")
    private String clazzCode;

    @ExcelProperty("年级")
    private String grade;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("状态")
    private String status;
}
