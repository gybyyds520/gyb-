package com.gyb.xqfx.service;

import com.gyb.xqfx.model.ml.MlFeatureRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部开源数据集加载器（天池 / UCI / xAPI 学情数据）。
 *
 * <p>读取 `ml/data/import_external.py` 生成的 `external_features.csv`
 * （FeatureRow/MlFeatureRow 对齐格式），并入训练数据集，回答"数据源与训练"：
 * 除校内历史学情外，可用公开数据集补充训练样本（外部样本以 term_id &lt; 0 标识，来源留痕于
 * `source_info.json`）。文件不存在或未配置时返回空，不影响原有链路。</p>
 */
@Slf4j
@Component
public class ExternalDatasetLoader {

    @Value("${xqfx.ml.external-dataset:}")
    private String externalDatasetPath;

    public boolean exists() {
        if (externalDatasetPath == null || externalDatasetPath.isBlank()) {
            return false;
        }
        return Files.exists(Paths.get(externalDatasetPath));
    }

    /** 读取外部特征 CSV；文件缺失/解析失败时返回空列表并告警（不影响主链路） */
    public List<MlFeatureRow> load() {
        if (!exists()) {
            return List.of();
        }
        try {
            return parse(Paths.get(externalDatasetPath));
        } catch (IOException e) {
            log.warn("读取外部训练数据集失败（将忽略）：{}", e.getMessage());
            return List.of();
        }
    }

    private List<MlFeatureRow> parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        String[] header = split(lines.get(0));
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            col.put(header[i].trim(), i);
        }
        List<MlFeatureRow> rows = new ArrayList<>(Math.max(0, lines.size() - 1));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] v = split(line);
            MlFeatureRow r = new MlFeatureRow();
            r.setStudentId(d(v, col, "student_id", 0L));
            r.setTermId(d(v, col, "term_id", -1L));
            r.setAvgScore(d(v, col, "avg_score", 0.0));
            r.setScoreTrend(d(v, col, "score_trend", 0.0));
            r.setScoreStd(d(v, col, "score_std", 0.0));
            r.setMinScore(d(v, col, "min_score", 0.0));
            r.setFailCount(i(v, col, "fail_count", 0));
            r.setFailRate(d(v, col, "fail_rate", 0.0));
            r.setGradePointAvg(d(v, col, "grade_point_avg", 0.0));
            r.setAttendanceRate(d(v, col, "attendance_rate", 1.0));
            r.setLateCount(i(v, col, "late_count", 0));
            r.setAbsentCount(i(v, col, "absent_count", 0));
            r.setAvgParticipation(d(v, col, "avg_participation", 0.0));
            r.setAvgHomework(d(v, col, "avg_homework", 0.0));
            r.setQuestionCount(i(v, col, "question_count", 0));
            r.setStudyMinutesAvg(d(v, col, "study_minutes_avg", 0.0));
            r.setVideoProgressAvg(d(v, col, "video_progress_avg", 0.0));
            r.setResourceViewCount(i(v, col, "resource_view_count", 0));
            r.setForumPostCount(i(v, col, "forum_post_count", 0));
            r.setAssignmentSubmitRate(d(v, col, "assignment_submit_rate", 0.0));
            r.setCreditTotal(d(v, col, "credit_total", 0.0));
            r.setCourseCount(i(v, col, "course_count", 1));
            Integer label = col.containsKey("label") ? i(v, col, "label", -1) : -1;
            if (label == 0 || label == 1) {
                r.setLabel(label);
            }
            rows.add(r);
        }
        return rows;
    }

    private static String[] split(String line) {
        return line.split(",", -1);
    }

    private static double d(String[] v, Map<String, Integer> col, String name, double def) {
        Integer idx = col.get(name);
        if (idx == null || idx >= v.length || v[idx] == null || v[idx].isBlank()) {
            return def;
        }
        try {
            return Double.parseDouble(v[idx].trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int i(String[] v, Map<String, Integer> col, String name, int def) {
        Integer idx = col.get(name);
        if (idx == null || idx >= v.length || v[idx] == null || v[idx].isBlank()) {
            return def;
        }
        try {
            return (int) Math.round(Double.parseDouble(v[idx].trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long d(String[] v, Map<String, Integer> col, String name, long def) {
        return (long) i(v, col, name, (int) def);
    }
}
