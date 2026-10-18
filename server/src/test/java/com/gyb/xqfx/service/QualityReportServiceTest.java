package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.entity.DataQualityReport;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.DataQualityReportMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.QualityIssue;
import com.gyb.xqfx.model.vo.QualityReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 数据质量报告服务测试
 */
@DisplayName("数据质量报告服务测试")
class QualityReportServiceTest {

    private DataQualityReportMapper reportMapper;
    private StudentMapper studentMapper;
    private ScoreMapper scoreMapper;
    private QualityReportService service;

    @BeforeEach
    void setUp() {
        reportMapper = Mockito.mock(DataQualityReportMapper.class);
        studentMapper = Mockito.mock(StudentMapper.class);
        scoreMapper = Mockito.mock(ScoreMapper.class);
        service = new QualityReportService(reportMapper, studentMapper, scoreMapper, new ObjectMapper());
    }

    // ==================== 学生校验 ====================

    @Test
    @DisplayName("学号或姓名缺失被识别")
    void testCheckStudentsMissing() {
        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("");
        s.setName(null);
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(s));

        List<QualityIssue> issues = service.checkStudents();
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(i -> "MISSING".equals(i.getProblemType())));
    }

    @Test
    @DisplayName("手机号与年级格式错误被识别")
    void testCheckStudentsFormat() {
        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("2021001");
        s.setName("张三");
        s.setPhone("12345");
        s.setGrade("2021级");
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(s));

        List<QualityIssue> issues = service.checkStudents();
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(i -> "OUT_OF_RANGE".equals(i.getProblemType())));
    }

    @Test
    @DisplayName("合规学生数据无问题")
    void testCheckStudentsValid() {
        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("2021001");
        s.setName("张三");
        s.setPhone("13800138000");
        s.setGrade("2021");
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(s));

        assertTrue(service.checkStudents().isEmpty());
    }

    // ==================== 成绩校验 ====================

    @Test
    @DisplayName("成绩越界被识别")
    void testCheckScoresOutOfRange() {
        Score sc = new Score();
        sc.setId(1L);
        sc.setTotalScore(new BigDecimal("120"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        List<QualityIssue> issues = service.checkScores();
        assertEquals(1, issues.size());
        assertEquals("OUT_OF_RANGE", issues.get(0).getProblemType());
    }

    @Test
    @DisplayName("总评缺失被识别且不重复计入一致性")
    void testCheckScoresMissing() {
        Score sc = new Score();
        sc.setId(2L);
        sc.setTotalScore(null);
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        List<QualityIssue> issues = service.checkScores();
        assertEquals(1, issues.size());
        assertEquals("MISSING", issues.get(0).getProblemType());
    }

    @Test
    @DisplayName("不及格却标记及格属于不一致")
    void testCheckScoresInconsistent() {
        Score sc = new Score();
        sc.setId(3L);
        sc.setTotalScore(new BigDecimal("45"));
        sc.setIsPass(1);
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        List<QualityIssue> issues = service.checkScores();
        assertEquals(1, issues.size());
        assertEquals("INCONSISTENT", issues.get(0).getProblemType());
    }

    @Test
    @DisplayName("负分被识别为越界")
    void testCheckScoresNegative() {
        Score sc = new Score();
        sc.setId(4L);
        sc.setUsualScore(new BigDecimal("-5"));
        sc.setTotalScore(new BigDecimal("80"));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        assertEquals(1, service.checkScores().size());
    }

    // ==================== 报告落库 ====================

    @Test
    @DisplayName("全量校验按数据集与问题类型分组落库")
    void testRunFullCheck() {
        Student s = new Student();
        s.setId(1L);
        s.setStudentNo("");
        Score sc = new Score();
        sc.setId(2L);
        sc.setTotalScore(new BigDecimal("150"));
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(s));
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of(sc));

        int count = service.runFullCheck(null);
        assertEquals(2, count);
        Mockito.verify(reportMapper, Mockito.times(2)).insert(any(DataQualityReport.class));
    }

    @Test
    @DisplayName("无问题时生成 0 条报告")
    void testRunFullCheckClean() {
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, service.runFullCheck(null));
        Mockito.verify(reportMapper, Mockito.never()).insert(any(DataQualityReport.class));
    }

    @Test
    @DisplayName("报告样例仅保留前 5 条且为合法 JSON")
    void testSampleJsonLimited() {
        Mockito.when(scoreMapper.selectList(any())).thenReturn(List.of());
        Student bad = new Student();
        bad.setId(1L);
        bad.setStudentNo("");
        bad.setName("");
        bad.setPhone("x");
        bad.setGrade("x");
        bad.setPhone("000");
        bad.setGrade("ab");
        Mockito.when(studentMapper.selectList(any())).thenReturn(List.of(bad));

        var captor = org.mockito.ArgumentCaptor.forClass(DataQualityReport.class);
        service.runFullCheck(null);
        Mockito.verify(reportMapper, Mockito.atLeastOnce()).insert(captor.capture());
        DataQualityReport report = captor.getValue();
        assertNotNull(report.getSampleJson());
        int size = assertDoesNotThrow(() -> new ObjectMapper().readTree(report.getSampleJson())).size();
        assertTrue(size <= 5, "样例不应超过 5 条");
    }

    // ==================== 汇总 ====================

    @Test
    @DisplayName("汇总统计问题总数与分布")
    void testSummary() {
        DataQualityReport r1 = new DataQualityReport();
        r1.setDataset("SCORE");
        r1.setProblemType("OUT_OF_RANGE");
        r1.setProblemCount(3);
        DataQualityReport r2 = new DataQualityReport();
        r2.setDataset("SCORE");
        r2.setProblemType("MISSING");
        r2.setProblemCount(2);
        Mockito.when(reportMapper.selectList(any())).thenReturn(List.of(r1, r2));
        Mockito.when(studentMapper.selectCount(any())).thenReturn(50L);
        Mockito.when(scoreMapper.selectCount(any())).thenReturn(950L);

        QualityReportVO vo = service.summary();
        assertEquals(2, vo.getReportCount());
        assertEquals(5, vo.getProblemCount());
        assertEquals(1000L, vo.getTotalRecords());
        assertEquals(3, vo.getByProblemType().get("OUT_OF_RANGE"));
        assertEquals(5, vo.getByDataset().get("SCORE"));
        // (1000-5)/1000*100 = 99.5
        assertEquals(0, new BigDecimal("99.50").compareTo(vo.getHealthRate()));
    }

    @Test
    @DisplayName("无记录时健康度为 100")
    void testSummaryEmpty() {
        Mockito.when(reportMapper.selectList(any())).thenReturn(List.of());
        Mockito.when(studentMapper.selectCount(any())).thenReturn(0L);
        Mockito.when(scoreMapper.selectCount(any())).thenReturn(0L);

        QualityReportVO vo = service.summary();
        assertEquals(0, vo.getProblemCount());
        assertEquals(0, new BigDecimal("100").compareTo(vo.getHealthRate()));
    }
}
