package com.gyb.xqfx.service;

import com.gyb.xqfx.model.QualityIssue;
import com.gyb.xqfx.model.excel.ScoreExcel;
import com.gyb.xqfx.model.excel.StudentExcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据质量校验：缺失 / 越界 / 重复 / 不一致
 */
@Slf4j
@Service
public class DataQualityService {

    private static final BigDecimal MIN_SCORE = new BigDecimal("0");
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

    /** 校验学生数据 */
    public List<QualityIssue> checkStudents(List<StudentExcel> rows) {
        List<QualityIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowIndex = i + 2;
            StudentExcel r = rows.get(i);
            if (r == null) {
                issues.add(new QualityIssue("STUDENT", "非空校验", "MISSING", rowIndex, "整行为空"));
                continue;
            }
            if (!StringUtils.hasText(r.getStudentNo())) {
                issues.add(new QualityIssue("STUDENT", "学号非空", "MISSING", rowIndex, "学号为空"));
            }
            if (!StringUtils.hasText(r.getName())) {
                issues.add(new QualityIssue("STUDENT", "姓名非空", "MISSING", rowIndex, "姓名为空"));
            }
            if (StringUtils.hasText(r.getStudentNo()) && !seen.add(r.getStudentNo())) {
                issues.add(new QualityIssue("STUDENT", "学号唯一", "DUPLICATE", rowIndex, "学号重复：" + r.getStudentNo()));
            }
            if (StringUtils.hasText(r.getGrade()) && !r.getGrade().matches("\\d{4}")) {
                issues.add(new QualityIssue("STUDENT", "年级格式", "OUT_OF_RANGE", rowIndex, "年级应为4位数字：" + r.getGrade()));
            }
            if (StringUtils.hasText(r.getPhone()) && !r.getPhone().matches("1\\d{10}")) {
                issues.add(new QualityIssue("STUDENT", "手机号格式", "OUT_OF_RANGE", rowIndex, "手机号格式错误：" + r.getPhone()));
            }
        }
        return issues;
    }

    /** 校验成绩数据 */
    public List<QualityIssue> checkScores(List<ScoreExcel> rows) {
        List<QualityIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowIndex = i + 2;
            ScoreExcel r = rows.get(i);
            if (r == null) {
                issues.add(new QualityIssue("SCORE", "非空校验", "MISSING", rowIndex, "整行为空"));
                continue;
            }
            if (!StringUtils.hasText(r.getStudentNo())) {
                issues.add(new QualityIssue("SCORE", "学号非空", "MISSING", rowIndex, "学号为空"));
            }
            if (!StringUtils.hasText(r.getCourseCode())) {
                issues.add(new QualityIssue("SCORE", "课程代码非空", "MISSING", rowIndex, "课程代码为空"));
            }
            String key = r.getStudentNo() + "#" + r.getCourseCode() + "#" + r.getTermCode();
            if (StringUtils.hasText(r.getStudentNo()) && !seen.add(key)) {
                issues.add(new QualityIssue("SCORE", "成绩唯一", "DUPLICATE", rowIndex, "同一学生同一课程重复：" + key));
            }
            checkRange(issues, rowIndex, "平时成绩", r.getUsualScore());
            checkRange(issues, rowIndex, "期中成绩", r.getMidScore());
            checkRange(issues, rowIndex, "期末成绩", r.getFinalScore());
            checkRange(issues, rowIndex, "总评成绩", r.getTotalScore());
        }
        return issues;
    }

    private void checkRange(List<QualityIssue> issues, int rowIndex, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(MIN_SCORE) < 0 || value.compareTo(MAX_SCORE) > 0) {
            issues.add(new QualityIssue("SCORE", field + "取值范围", "OUT_OF_RANGE", rowIndex,
                    field + "应在 0~100 之间，实际：" + value));
        }
    }
}
