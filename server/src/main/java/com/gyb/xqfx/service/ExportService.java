package com.gyb.xqfx.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据导出服务
 *
 * <p>提供带文档化 Schema 的原始数据导出，支持院校基于导出数据自行建模或
 * 迁移分析（行业公认的早期预警系统必要能力之一）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    /** 各数据集的字段 Schema：字段名 → 说明 */
    private static final Map<String, List<String[]>> SCHEMA = Map.of(
            "score", List.of(
                    new String[]{"student_no", "学号"},
                    new String[]{"student_name", "姓名"},
                    new String[]{"term_id", "学期ID"},
                    new String[]{"course_id", "课程ID"},
                    new String[]{"usual_score", "平时成绩"},
                    new String[]{"mid_score", "期中成绩"},
                    new String[]{"final_score", "期末成绩"},
                    new String[]{"total_score", "总评成绩"},
                    new String[]{"grade_point", "绩点"},
                    new String[]{"is_pass", "是否及格(1是/0否)"},
                    new String[]{"exam_status", "考试状态"}),
            "warning", List.of(
                    new String[]{"warning_no", "预警编号"},
                    new String[]{"student_no", "学号"},
                    new String[]{"student_name", "姓名"},
                    new String[]{"warning_level", "预警等级(1蓝/2黄/3橙/4红)"},
                    new String[]{"warning_type", "预警类型"},
                    new String[]{"risk_probability", "风险概率"},
                    new String[]{"status", "状态"},
                    new String[]{"term_id", "学期ID"},
                    new String[]{"trigger_detail", "触发依据"}),
            "ticket", List.of(
                    new String[]{"ticket_no", "工单号"},
                    new String[]{"student_no", "学号"},
                    new String[]{"student_name", "姓名"},
                    new String[]{"intervene_type", "干预方式"},
                    new String[]{"status", "状态(OPEN/PROCESSING/CLOSED)"},
                    new String[]{"sla_hours", "承诺时限(小时)"},
                    new String[]{"overdue_hours", "超时时长(小时)"},
                    new String[]{"escalate_level", "升级层级"},
                    new String[]{"start_date", "开始日期"},
                    new String[]{"due_date", "截止日期"},
                    new String[]{"close_date", "关闭日期"},
                    new String[]{"effect_eval", "效果评估"},
                    new String[]{"feedback", "有效性反馈"}),
            "indicator", List.of(
                    new String[]{"student_no", "学号"},
                    new String[]{"student_name", "姓名"},
                    new String[]{"gpa", "学分绩点"},
                    new String[]{"total_credit", "修读学分"},
                    new String[]{"earned_credit", "已获学分"},
                    new String[]{"deficit_credit", "欠学分"},
                    new String[]{"fail_count", "不及格门数"},
                    new String[]{"trend_slope", "成绩趋势斜率"},
                    new String[]{"deviation", "绩点偏离度(标准差倍数)"},
                    new String[]{"risk_flag", "风险判定(NORMAL/ATTENTION/RISK)"},
                    new String[]{"reason", "判定依据"})
    );

    private final ScoreMapper scoreMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final InterventionTicketMapper ticketMapper;
    private final StudentMapper studentMapper;
    private final AcademicIndicatorService indicatorService;

    /**
     * 导出成绩数据
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public void exportScores(HttpServletResponse response, Long termId, java.util.List<Long> studentIds)
            throws IOException {
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(termId != null, Score::getTermId, termId)
                .in(studentIds != null && !studentIds.isEmpty(), Score::getStudentId, studentIds));
        List<List<Object>> rows = new ArrayList<>();
        for (Score sc : scores) {
            Student s = studentMapper.selectById(sc.getStudentId());
            List<Object> row = new ArrayList<>();
            row.add(s == null ? "" : s.getStudentNo());
            row.add(s == null ? "" : s.getName());
            row.add(sc.getTermId());
            row.add(sc.getCourseId());
            row.add(sc.getUsualScore());
            row.add(sc.getMidScore());
            row.add(sc.getFinalScore());
            row.add(sc.getTotalScore());
            row.add(sc.getGradePoint());
            row.add(sc.getIsPass());
            row.add(sc.getExamStatus());
            rows.add(row);
        }
        write(response, "score", "成绩数据", rows);
    }

    /**
     * 导出预警数据
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public void exportWarnings(HttpServletResponse response, Long termId, java.util.List<Long> studentIds)
            throws IOException {
        List<WarningRecord> records = warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                .eq(termId != null, WarningRecord::getTermId, termId)
                .in(studentIds != null && !studentIds.isEmpty(), WarningRecord::getStudentId, studentIds));
        List<List<Object>> rows = new ArrayList<>();
        for (WarningRecord r : records) {
            Student s = studentMapper.selectById(r.getStudentId());
            List<Object> row = new ArrayList<>();
            row.add(r.getWarningNo());
            row.add(s == null ? "" : s.getStudentNo());
            row.add(s == null ? "" : s.getName());
            row.add(r.getWarningLevel());
            row.add(r.getWarningType());
            row.add(r.getRiskProbability());
            row.add(r.getStatus());
            row.add(r.getTermId());
            row.add(r.getTriggerDetail());
            rows.add(row);
        }
        write(response, "warning", "预警数据", rows);
    }

    /**
     * 导出干预工单数据
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public void exportTickets(HttpServletResponse response, java.util.List<Long> studentIds) throws IOException {
        List<InterventionTicket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<InterventionTicket>()
                .in(studentIds != null && !studentIds.isEmpty(), InterventionTicket::getStudentId, studentIds));
        List<List<Object>> rows = new ArrayList<>();
        for (InterventionTicket t : tickets) {
            Student s = studentMapper.selectById(t.getStudentId());
            List<Object> row = new ArrayList<>();
            row.add(t.getTicketNo());
            row.add(s == null ? "" : s.getStudentNo());
            row.add(s == null ? "" : s.getName());
            row.add(t.getInterveneType());
            row.add(t.getStatus());
            row.add(t.getSlaHours());
            row.add(t.getOverdueHours());
            row.add(t.getEscalateLevel());
            row.add(t.getStartDate());
            row.add(t.getDueDate());
            row.add(t.getCloseDate());
            row.add(t.getEffectEval());
            row.add(t.getFeedback());
            rows.add(row);
        }
        write(response, "ticket", "干预工单", rows);
    }

    /**
     * 导出学业指标数据
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量）
     */
    public void exportIndicators(HttpServletResponse response, Long termId, java.util.List<Long> studentIds)
            throws IOException {
        List<AcademicIndicatorVO> list = indicatorService.batchCalculate(termId, 0);
        if (studentIds != null && !studentIds.isEmpty()) {
            // 按数据范围过滤：学号集合 → 允许导出的学号
            List<Student> scoped = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .in(Student::getId, studentIds));
            java.util.Set<String> allowedNos = scoped.stream().map(Student::getStudentNo).collect(java.util.stream.Collectors.toSet());
            list = list.stream().filter(v -> v.getStudentNo() != null && allowedNos.contains(v.getStudentNo()))
                    .collect(java.util.stream.Collectors.toList());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (AcademicIndicatorVO v : list) {
            List<Object> row = new ArrayList<>();
            row.add(v.getStudentNo());
            row.add(v.getStudentName());
            row.add(v.getGpa());
            row.add(v.getTotalCredit());
            row.add(v.getEarnedCredit());
            row.add(v.getDeficitCredit());
            row.add(v.getFailCount());
            row.add(v.getTrendSlope());
            row.add(v.getDeviation());
            row.add(v.getRiskFlag());
            row.add(v.getReason());
            rows.add(row);
        }
        write(response, "indicator", "学业指标", rows);
    }

    /**
     * 查询某数据集的字段 Schema，供导出前确认结构
     */
    public List<Map<String, String>> schema(String type) {
        List<String[]> fields = SCHEMA.get(type);
        if (fields == null) {
            throw new IllegalArgumentException("不支持的数据集：" + type);
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (String[] f : fields) {
            result.add(Map.of("field", f[0], "desc", f[1]));
        }
        return result;
    }

    /** 写出 Excel：表头使用字段名与中文说明两行，便于外部系统识别 */
    private void write(HttpServletResponse response, String type, String sheetName,
                       List<List<Object>> rows) throws IOException {
        List<String[]> fields = SCHEMA.get(type);
        List<List<String>> head = new ArrayList<>();
        for (String[] f : fields) {
            List<String> col = new ArrayList<>();
            col.add(f[1]);
            col.add(f[0]);
            head.add(col);
        }
        String fileName = URLEncoder.encode(sheetName, StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
        try (OutputStream os = response.getOutputStream()) {
            EasyExcel.write(os).head(head).sheet(sheetName).doWrite(rows);
        }
        log.info("导出 {} 完成，共 {} 行", sheetName, rows.size());
    }
}
