package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.entity.DataQualityReport;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.DataQualityReportMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.QualityIssue;
import com.gyb.xqfx.model.vo.QualityReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量报告服务：执行全量校验并将结果沉淀到 data_quality_report
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityReportService {

    private static final int SAMPLE_SIZE = 5;

    private final DataQualityReportMapper reportMapper;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final ObjectMapper objectMapper;

    /**
     * 对库内全量学生与成绩执行校验并落库
     *
     * @return 新增报告条数
     */
    public int runFullCheck(Long taskId) {
        List<QualityIssue> issues = new ArrayList<>();
        issues.addAll(checkStudents());
        issues.addAll(checkScores());

        Map<String, List<QualityIssue>> grouped = new LinkedHashMap<>();
        for (QualityIssue issue : issues) {
            grouped.computeIfAbsent(issue.getDataset() + "|" + issue.getProblemType(), k -> new ArrayList<>())
                    .add(issue);
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Map.Entry<String, List<QualityIssue>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            List<QualityIssue> list = entry.getValue();
            DataQualityReport report = new DataQualityReport();
            report.setTaskId(taskId);
            report.setDataset(parts[0]);
            report.setProblemType(parts[1]);
            report.setCheckRule(list.get(0).getCheckRule());
            report.setProblemCount(list.size());
            report.setCheckTime(now);
            report.setCreateTime(now);
            report.setUpdateTime(now);
            report.setIsDeleted(0);
            report.setSampleJson(toSampleJson(list));
            reportMapper.insert(report);
            count++;
        }
        log.info("数据质量校验完成：问题 {} 条，生成报告 {} 条", issues.size(), count);
        return count;
    }

    /**
     * 报告列表
     */
    public List<DataQualityReport> list(String dataset) {
        return reportMapper.selectList(new LambdaQueryWrapper<DataQualityReport>()
                .eq(dataset != null && !dataset.isBlank(), DataQualityReport::getDataset, dataset)
                .orderByDesc(DataQualityReport::getId));
    }

    /**
     * 汇总视图：总问题数、按类型/数据集分布、数据完整率
     */
    public QualityReportVO summary() {
        List<DataQualityReport> reports = list(null);
        QualityReportVO vo = new QualityReportVO();
        vo.setReportCount(reports.size());
        int total = reports.stream().mapToInt(r -> r.getProblemCount() == null ? 0 : r.getProblemCount()).sum();
        vo.setProblemCount(total);

        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byDataset = new LinkedHashMap<>();
        for (DataQualityReport r : reports) {
            byType.merge(r.getProblemType(), r.getProblemCount() == null ? 0 : r.getProblemCount(), Integer::sum);
            byDataset.merge(r.getDataset(), r.getProblemCount() == null ? 0 : r.getProblemCount(), Integer::sum);
        }
        vo.setByProblemType(byType);
        vo.setByDataset(byDataset);

        long studentCount = studentMapper.selectCount(null);
        long scoreCount = scoreMapper.selectCount(null);
        long totalRecords = studentCount + scoreCount;
        vo.setTotalRecords(totalRecords);
        vo.setHealthRate(totalRecords == 0 ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(100L * (totalRecords - total)).divide(
                BigDecimal.valueOf(totalRecords), 2, java.math.RoundingMode.HALF_UP));
        return vo;
    }

    /** 校验库内学生数据（学号/手机号缺失、手机号格式、年级格式） */
    public List<QualityIssue> checkStudents() {
        List<QualityIssue> issues = new ArrayList<>();
        List<Student> students = studentMapper.selectList(null);
        for (Student s : students) {
            if (!hasText(s.getStudentNo())) {
                issues.add(new QualityIssue("STUDENT", "学号非空", "MISSING", 0, "学号为空，学生ID=" + s.getId()));
            }
            if (!hasText(s.getName())) {
                issues.add(new QualityIssue("STUDENT", "姓名非空", "MISSING", 0, "姓名为空，学生ID=" + s.getId()));
            }
            if (hasText(s.getPhone()) && !s.getPhone().matches("1\\d{10}")) {
                issues.add(new QualityIssue("STUDENT", "手机号格式", "OUT_OF_RANGE", 0,
                        "手机号格式错误：" + s.getPhone()));
            }
            if (hasText(s.getGrade()) && !s.getGrade().matches("\\d{4}")) {
                issues.add(new QualityIssue("STUDENT", "年级格式", "OUT_OF_RANGE", 0, "年级格式错误：" + s.getGrade()));
            }
        }
        return issues;
    }

    /** 校验库内成绩数据（越界、缺失、逻辑不一致） */
    public List<QualityIssue> checkScores() {
        List<QualityIssue> issues = new ArrayList<>();
        List<Score> scores = scoreMapper.selectList(null);
        for (Score sc : scores) {
            checkRange(issues, sc.getId(), "平时成绩", sc.getUsualScore());
            checkRange(issues, sc.getId(), "期中成绩", sc.getMidScore());
            checkRange(issues, sc.getId(), "期末成绩", sc.getFinalScore());
            checkRange(issues, sc.getId(), "总评成绩", sc.getTotalScore());
            if (sc.getTotalScore() == null) {
                issues.add(new QualityIssue("SCORE", "总评成绩非空", "MISSING", 0, "总评成绩为空，记录ID=" + sc.getId()));
                continue;
            }
            // 一致性：总评低于 60 却标记为及格
            if (sc.getIsPass() != null && sc.getIsPass() == 1 && sc.getTotalScore().compareTo(new BigDecimal("60")) < 0) {
                issues.add(new QualityIssue("SCORE", "及格标记一致性", "INCONSISTENT", 0,
                        "总评 " + sc.getTotalScore() + " 却标记为及格，记录ID=" + sc.getId()));
            }
        }
        return issues;
    }

    private void checkRange(List<QualityIssue> issues, Long id, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            issues.add(new QualityIssue("SCORE", field + "取值范围", "OUT_OF_RANGE", 0,
                    field + "应在 0~100 之间，实际：" + value + "，记录ID=" + id));
        }
    }

    private String toSampleJson(List<QualityIssue> issues) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < Math.min(SAMPLE_SIZE, issues.size()); i++) {
            QualityIssue issue = issues.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("row", issue.getRowIndex());
            m.put("rule", issue.getCheckRule());
            m.put("message", issue.getMessage());
            samples.add(m);
        }
        try {
            return objectMapper.writeValueAsString(samples);
        } catch (Exception e) {
            log.warn("序列化问题样例失败：{}", e.getMessage());
            return "[]";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
