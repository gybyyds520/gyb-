package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.model.ml.MlFeatureRow;
import com.gyb.xqfx.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 机器学习训练数据源诊断。
 *
 * <p>回答"预测模型的数据源与训练"：直接基于现有训练数据构建链路统计样本量、
 * 类别分布、学期跨度与特征覆盖率，判定训练数据健康度（READY / COLD_START / IMBALANCED）。</p>
 */
@Tag(name = "ML 训练数据诊断")
@RestController
@RequestMapping("/ml")
@RequiredArgsConstructor
public class MlDatasetController {

    private final PredictionService predictionService;

    @Operation(summary = "训练数据集健康诊断")
    @GetMapping("/dataset-summary")
    public Result<Map<String, Object>> datasetSummary() {
        List<MlFeatureRow> dataset = predictionService.buildTrainingDataset();

        int total = dataset.size();
        long riskCount = dataset.stream().filter(r -> r.getLabel() != null && r.getLabel() == 1).count();
        long safeCount = total - riskCount;

        Set<Long> termIds = new HashSet<>();
        Set<Long> studentIds = new HashSet<>();
        for (MlFeatureRow row : dataset) {
            if (row.getTermId() != null) {
                termIds.add(row.getTermId());
            }
            if (row.getStudentId() != null) {
                studentIds.add(row.getStudentId());
            }
        }

        Map<String, Double> coverage = new HashMap<>();
        addCoverage(coverage, dataset, "avg_score", r -> r.getAvgScore());
        addCoverage(coverage, dataset, "score_trend", r -> r.getScoreTrend());
        addCoverage(coverage, dataset, "attendance_rate", r -> r.getAttendanceRate());
        addCoverage(coverage, dataset, "avg_homework", r -> r.getAvgHomework());
        addCoverage(coverage, dataset, "avg_participation", r -> r.getAvgParticipation());
        addCoverage(coverage, dataset, "study_minutes_avg", r -> r.getStudyMinutesAvg());
        addCoverage(coverage, dataset, "video_progress_avg", r -> r.getVideoProgressAvg());
        addCoverage(coverage, dataset, "assignment_submit_rate", r -> r.getAssignmentSubmitRate());

        String status;
        String suggestion;
        if (total == 0) {
            status = "COLD_START";
            suggestion = "当前无可训练样本：请接入真实历史成绩/考勤数据，或启动演示数据（bootstrap.auto-demo）后再训练。";
        } else if (total < 100) {
            status = "COLD_START";
            suggestion = "样本量偏少（" + total + " 条），模型易过拟合；建议接入更多历史学期数据或增加学生规模。";
        } else if (riskCount == 0 || safeCount == 0) {
            status = "IMBALANCED";
            suggestion = "类别严重不均衡（风险 " + riskCount + "/正常 " + safeCount + "），建议补充高风险样本或开启类别权重训练。";
        } else {
            status = "READY";
            suggestion = "样本量 " + total + " 条、覆盖 " + termIds.size() + " 个学期、风险样本 " + riskCount
                    + " 条，可支撑 LR/RF/XGB 训练与评估。";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalSamples", total);
        data.put("studentCount", studentIds.size());
        data.put("termSpan", termIds.size());
        data.put("riskCount", riskCount);
        data.put("safeCount", safeCount);
        data.put("riskRate", total == 0 ? 0.0 : Math.round(riskCount * 1000.0 / total) / 10.0);
        data.put("featureCoverage", coverage);
        data.put("status", status);
        data.put("suggestion", suggestion);
        return Result.success(data);
    }

    private void addCoverage(Map<String, Double> coverage, List<MlFeatureRow> rows,
                             String name, Function<MlFeatureRow, Object> getter) {
        if (rows.isEmpty()) {
            coverage.put(name, 0.0);
            return;
        }
        long nonNull = rows.stream().filter(r -> getter.apply(r) != null).count();
        coverage.put(name, Math.round(nonNull * 1000.0 / rows.size()) / 10.0);
    }
}
