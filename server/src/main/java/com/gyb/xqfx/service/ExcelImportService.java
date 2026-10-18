package com.gyb.xqfx.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.ImportTask;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.excel.ExcelDataListener;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CollegeMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ImportTaskMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.QualityIssue;
import com.gyb.xqfx.model.excel.ScoreExcel;
import com.gyb.xqfx.model.excel.StudentExcel;
import com.gyb.xqfx.service.impl.ScoreServiceImpl;
import com.gyb.xqfx.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Excel 批量导入：解析 → 质量校验 → 落库 → 生成导入任务与质量报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ImportTaskMapper importTaskMapper;
    private final DataQualityService dataQualityService;
    private final StudentService studentService;
    private final ScoreService scoreService;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final CourseMapper courseMapper;
    private final TermMapper termMapper;

    /** 导入学生 */
    public ImportTask importStudents(MultipartFile file) throws IOException {
        ImportTask task = newTask("STUDENT", file.getOriginalFilename());

        List<StudentExcel> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), StudentExcel.class,
                        new ExcelDataListener<StudentExcel>(rows::addAll))
                .sheet().doRead();

        List<QualityIssue> issues = dataQualityService.checkStudents(rows);
        Set<String> badKeys = new java.util.HashSet<>();
        for (QualityIssue issue : issues) {
            // 缺失与越界按行号剔除；重复保留首行
            badKeys.add(String.valueOf(issue.getRowIndex()));
        }

        List<Student> valid = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (badKeys.contains(String.valueOf(i + 2))) {
                continue;
            }
            StudentExcel r = rows.get(i);
            Student s = new Student();
            s.setStudentNo(r.getStudentNo());
            s.setName(r.getName());
            s.setGender(r.getGender());
            s.setGrade(r.getGrade());
            s.setPhone(MaskUtils.maskPhone(r.getPhone()));
            s.setStatus(StringUtils.hasText(r.getStatus()) ? r.getStatus() : "ENROLLED");
            s.setCollegeId(idByCode(collegeMapper.selectList(null), r.getCollegeCode(), "getCollegeCode", "getId"));
            s.setMajorId(idByCode(majorMapper.selectList(null), r.getMajorCode(), "getMajorCode", "getId"));
            s.setClazzId(idByCode(clazzMapper.selectList(null), r.getClazzCode(), "getClazzCode", "getId"));
            valid.add(s);
        }

        if (!valid.isEmpty()) {
            studentService.saveBatch(valid, 500);
        }
        return finishTask(task, rows.size(), valid.size(), rows.size() - valid.size(), issues);
    }

    /** 导入成绩 */
    public ImportTask importScores(MultipartFile file) throws IOException {
        ImportTask task = newTask("SCORE", file.getOriginalFilename());

        List<ScoreExcel> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), ScoreExcel.class,
                        new ExcelDataListener<ScoreExcel>(rows::addAll))
                .sheet().doRead();

        List<QualityIssue> issues = dataQualityService.checkScores(rows);
        java.util.Set<String> badRows = new java.util.HashSet<>();
        for (QualityIssue issue : issues) {
            badRows.add(String.valueOf(issue.getRowIndex()));
        }

        // 预加载映射
        Map<String, Long> termIds = new HashMap<>();
        for (Term t : termMapper.selectList(null)) {
            termIds.put(t.getTermCode(), t.getId());
        }
        Map<String, Long> courseIds = new HashMap<>();
        for (Course c : courseMapper.selectList(null)) {
            courseIds.put(c.getCourseCode(), c.getId());
        }
        List<String> studentNos = rows.stream().map(ScoreExcel::getStudentNo)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, Long> studentIds = studentService.mapStudentNoToId(studentNos);

        List<Score> valid = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (badRows.contains(String.valueOf(i + 2))) {
                continue;
            }
            ScoreExcel r = rows.get(i);
            Long sid = studentIds.get(r.getStudentNo());
            Long cid = courseIds.get(r.getCourseCode());
            Long tid = termIds.get(r.getTermCode());
            if (sid == null || cid == null || tid == null) {
                issues.add(new QualityIssue("SCORE", "外键存在性", "INCONSISTENT", i + 2,
                        "学号/课程/学期未找到对应记录"));
                continue;
            }
            Score s = new Score();
            s.setStudentId(sid);
            s.setCourseId(cid);
            s.setTermId(tid);
            s.setCourseOfferingId(cid);
            s.setUsualScore(r.getUsualScore());
            s.setMidScore(r.getMidScore());
            s.setFinalScore(r.getFinalScore());
            s.setTotalScore(r.getTotalScore());
            s.setExamStatus(StringUtils.hasText(r.getExamStatus()) ? r.getExamStatus() : "NORMAL");
            BigDecimal total = r.getTotalScore() != null ? r.getTotalScore() : BigDecimal.ZERO;
            s.setIsPass(total.compareTo(new BigDecimal("60")) >= 0 ? 1 : 0);
            s.setGradePoint(ScoreServiceImpl.calcGradePoint(total));
            s.setIsRetake(0);
            valid.add(s);
        }

        if (!valid.isEmpty()) {
            scoreService.saveBatch(valid, 500);
        }
        return finishTask(task, rows.size(), valid.size(), rows.size() - valid.size(), issues);
    }

    // ---------- 私有辅助 ----------

    private ImportTask newTask(String type, String fileName) {
        ImportTask task = new ImportTask();
        task.setTaskNo(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        task.setImportType(type);
        task.setFileName(fileName);
        task.setStatus("RUNNING");
        task.setStartTime(java.time.LocalDateTime.now());
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        importTaskMapper.insert(task);
        return task;
    }

    private ImportTask finishTask(ImportTask task, int total, int success, int fail, List<QualityIssue> issues) {
        task.setTotalCount(total);
        task.setSuccessCount(success);
        task.setFailCount(fail);
        task.setStatus(fail == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        task.setEndTime(java.time.LocalDateTime.now());
        task.setErrorReportUrl(issues.isEmpty() ? null : summarize(issues));
        importTaskMapper.updateById(task);
        log.info("导入完成 type={} total={} success={} fail={}", task.getImportType(), total, success, fail);
        return task;
    }

    /** 将质量问题压缩为可读摘要（前 20 条） */
    private String summarize(List<QualityIssue> issues) {
        return issues.stream()
                .limit(20)
                .map(i -> "第" + i.getRowIndex() + "行[" + i.getProblemType() + "]" + i.getMessage())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    /** 按编码属性查找实体 ID */
    private <E> Long idByCode(List<E> list, String code, String codeGetter, String idGetter) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        for (E e : list) {
            try {
                Object c = e.getClass().getMethod(codeGetter).invoke(e);
                if (code.equals(c)) {
                    return (Long) e.getClass().getMethod(idGetter).invoke(e);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("反射读取编码失败", ex);
            }
        }
        return null;
    }
}
