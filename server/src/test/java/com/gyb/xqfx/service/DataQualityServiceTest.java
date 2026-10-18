package com.gyb.xqfx.service;

import com.gyb.xqfx.model.QualityIssue;
import com.gyb.xqfx.model.excel.ScoreExcel;
import com.gyb.xqfx.model.excel.StudentExcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据质量校验测试
 */
@DisplayName("数据质量校验服务测试")
class DataQualityServiceTest {

    private DataQualityService service;

    @BeforeEach
    void setUp() {
        service = new DataQualityService();
    }

    private StudentExcel student(String no, String name, String grade, String phone) {
        StudentExcel s = new StudentExcel();
        s.setStudentNo(no);
        s.setName(name);
        s.setGender("男");
        s.setCollegeCode("C01");
        s.setMajorCode("M0101");
        s.setClazzCode("M010101");
        s.setGrade(grade);
        s.setPhone(phone);
        s.setStatus("ENROLLED");
        return s;
    }

    @Test
    @DisplayName("合法数据不应产生问题")
    void testValidStudents() {
        List<QualityIssue> issues = service.checkStudents(
                List.of(student("2301132013", "张三", "2023", "13812345678")));
        assertTrue(issues.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("学号缺失应被识别")
    void testMissingStudentNo(String no) {
        List<QualityIssue> issues = service.checkStudents(List.of(student(no, "张三", "2023", "13812345678")));
        assertTrue(issues.stream().anyMatch(i -> "MISSING".equals(i.getProblemType())));
    }

    @Test
    @DisplayName("姓名缺失应被识别")
    void testMissingName() {
        List<QualityIssue> issues = service.checkStudents(List.of(student("2301132013", "", "2023", "13812345678")));
        assertTrue(issues.stream().anyMatch(i -> i.getMessage().contains("姓名")));
    }

    @Test
    @DisplayName("学号重复应被识别")
    void testDuplicateStudentNo() {
        List<QualityIssue> issues = service.checkStudents(List.of(
                student("2301132013", "张三", "2023", "13812345678"),
                student("2301132013", "李四", "2023", "13812345679")));
        assertEquals(1, issues.stream().filter(i -> "DUPLICATE".equals(i.getProblemType())).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023", "2024", "2025"})
    @DisplayName("合法年级不应报错")
    void testValidGrade(String grade) {
        List<QualityIssue> issues = service.checkStudents(List.of(student("2301132013", "张三", grade, "13812345678")));
        assertTrue(issues.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"23", "二零二三", "20231"})
    @DisplayName("非法年级应被识别为越界")
    void testInvalidGrade(String grade) {
        List<QualityIssue> issues = service.checkStudents(List.of(student("2301132013", "张三", grade, "13812345678")));
        assertTrue(issues.stream().anyMatch(i -> "OUT_OF_RANGE".equals(i.getProblemType())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "2381234567", "abcdefghijk"})
    @DisplayName("非法手机号应被识别")
    void testInvalidPhone(String phone) {
        List<QualityIssue> issues = service.checkStudents(List.of(student("2301132013", "张三", "2023", phone)));
        assertTrue(issues.stream().anyMatch(i -> i.getMessage().contains("手机号")));
    }

    @ParameterizedTest(name = "成绩 {0} 是否越界：{1}")
    @CsvSource({
            "0.0, false",
            "59.9, false",
            "100.0, false",
            "-1.0, true",
            "101.0, true",
            "1000.0, true"
    })
    @DisplayName("成绩取值范围校验")
    void testScoreRange(BigDecimal score, boolean expectIssue) {
        ScoreExcel r = new ScoreExcel();
        r.setStudentNo("2301132013");
        r.setCourseCode("CS001");
        r.setTermCode("2026-2027-1");
        r.setTotalScore(score);
        List<QualityIssue> issues = service.checkScores(List.of(r));
        boolean hasRangeIssue = issues.stream().anyMatch(i -> "OUT_OF_RANGE".equals(i.getProblemType()));
        assertEquals(expectIssue, hasRangeIssue);
    }

    @Test
    @DisplayName("空对象行应被识别为缺失")
    void testNullRow() {
        List<QualityIssue> issues = service.checkStudents(java.util.Collections.singletonList(null));
        assertEquals(1, issues.size());
        assertEquals("MISSING", issues.get(0).getProblemType());
    }

    @Test
    @DisplayName("成绩行号应从第2行开始")
    void testRowIndex() {
        ScoreExcel r = new ScoreExcel();
        r.setStudentNo("2301132013");
        r.setCourseCode("CS001");
        r.setTermCode("2026-2027-1");
        r.setTotalScore(new BigDecimal("120"));
        List<QualityIssue> issues = service.checkScores(List.of(r));
        assertEquals(2, issues.get(0).getRowIndex());
    }
}
