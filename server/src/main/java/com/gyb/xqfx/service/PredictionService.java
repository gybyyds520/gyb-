package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.MlServiceClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.ClassPerformance;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.FeatureRecord;
import com.gyb.xqfx.entity.MlModel;
import com.gyb.xqfx.entity.OnlineBehavior;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClassPerformanceMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.FeatureRecordMapper;
import com.gyb.xqfx.mapper.MlModelMapper;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.ml.MlDtos;
import com.gyb.xqfx.model.ml.MlFeatureRow;
import com.gyb.xqfx.service.impl.ScoreServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学业风险预测服务：特征工程 → 模型训练 → 批量推理 → 结果落库 → 触发预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    /** 风险等级 -> 数值（1蓝 2黄 3橙 4红） */
    public static final Map<String, Integer> LEVEL_CODE = Map.of(
            "BLUE", 1, "YELLOW", 2, "ORANGE", 3, "RED", 4);

    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final ClassPerformanceMapper classPerformanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;
    private final CourseMapper courseMapper;
    private final TermMapper termMapper;
    private final StudentMapper studentMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final FeatureRecordMapper featureRecordMapper;
    private final MlModelMapper mlModelMapper;
    private final MlServiceClient mlServiceClient;
    private final WarningService warningService;
    private final ObjectMapper objectMapper;
    private final ExternalDatasetLoader externalDatasetLoader;

    // ==================== 特征工程 ====================

    /**
     * 构建指定学期全体学生的特征行
     */
    public List<MlFeatureRow> buildFeatures(Long termId) {
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getStatus, "ENROLLED"));
        if (students.isEmpty()) {
            throw new BusinessException("暂无在读学生，无法构建特征");
        }

        List<Term> terms = termMapper.selectList(null);
        terms.sort(Comparator.comparing(Term::getId));
        Map<Long, Double> prevAvgByStudent = new HashMap<>();
        Map<Long, Map<Long, List<Score>>> scoresByStudentTerm = new HashMap<>();

        List<Score> allScores = scoreMapper.selectList(null);
        for (Score s : allScores) {
            scoresByStudentTerm
                    .computeIfAbsent(s.getStudentId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getTermId(), k -> new ArrayList<>())
                    .add(s);
        }

        // 上一学期平均成绩，用于计算成绩趋势
        Long prevTermId = previousTermId(terms, termId);
        if (prevTermId != null) {
            for (Student stu : students) {
                prevAvgByStudent.put(stu.getId(), avgScore(scoresByStudentTerm
                        .getOrDefault(stu.getId(), Map.of()).get(prevTermId)));
            }
        }

        Map<Long, List<Attendance>> attendanceByStudent = attendanceMapper
                .selectList(new LambdaQueryWrapper<Attendance>().eq(Attendance::getTermId, termId))
                .stream().collect(Collectors.groupingBy(Attendance::getStudentId));
        Map<Long, List<ClassPerformance>> performanceByStudent = classPerformanceMapper
                .selectList(new LambdaQueryWrapper<ClassPerformance>().eq(ClassPerformance::getTermId, termId))
                .stream().collect(Collectors.groupingBy(ClassPerformance::getStudentId));
        Map<Long, List<OnlineBehavior>> behaviorByStudent = onlineBehaviorMapper
                .selectList(new LambdaQueryWrapper<OnlineBehavior>().eq(OnlineBehavior::getTermId, termId))
                .stream().collect(Collectors.groupingBy(OnlineBehavior::getStudentId));

        List<Course> courses = courseMapper.selectList(null);
        Map<Long, Course> courseById = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<MlFeatureRow> rows = new ArrayList<>();
        for (Student stu : students) {
            List<Score> scores = scoresByStudentTerm
                    .getOrDefault(stu.getId(), Map.of()).getOrDefault(termId, List.of());
            rows.add(buildRow(stu, termId, scores,
                    prevAvgByStudent.get(stu.getId()),
                    attendanceByStudent.getOrDefault(stu.getId(), List.of()),
                    performanceByStudent.getOrDefault(stu.getId(), List.of()),
                    behaviorByStudent.getOrDefault(stu.getId(), List.of()),
                    courseById));
        }
        return rows;
    }

    private MlFeatureRow buildRow(Student stu, Long termId, List<Score> scores,
                                  Double prevAvg,
                                  List<Attendance> attendances,
                                  List<ClassPerformance> performances,
                                  List<OnlineBehavior> behaviors,
                                  Map<Long, Course> courseById) {
        double avg = avgScore(scores);
        double prev = prevAvg == null ? avg : prevAvg;
        int failCount = (int) scores.stream()
                .filter(s -> s.getTotalScore() != null
                        && s.getTotalScore().compareTo(new BigDecimal("60")) < 0)
                .count();
        double failRate = scores.isEmpty() ? 0.0 : (double) failCount / scores.size();
        double minScore = scores.stream()
                .map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).min().orElse(0.0);
        double std = stdDev(scores);
        double gpaAvg = scores.stream()
                .map(s -> ScoreServiceImpl.calcGradePoint(s.getTotalScore()))
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);

        long present = attendances.stream().filter(a -> "PRESENT".equals(a.getStatus())).count();
        double attendanceRate = attendances.isEmpty() ? 1.0 : (double) present / attendances.size();
        int lateCount = (int) attendances.stream().filter(a -> "LATE".equals(a.getStatus())).count();
        int absentCount = (int) attendances.stream()
                .filter(a -> "ABSENT".equals(a.getStatus()) || "EARLY".equals(a.getStatus())).count();

        double participation = performances.stream()
                .map(ClassPerformance::getParticipationScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        double homework = performances.stream()
                .map(ClassPerformance::getHomeworkScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        int questionCount = performances.stream()
                .map(ClassPerformance::getQuestionCount).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();

        double studyMinutes = behaviors.stream()
                .mapToInt(b -> b.getStudyMinutes() == null ? 0 : b.getStudyMinutes()).average().orElse(0.0);
        double videoProgress = behaviors.stream()
                .map(OnlineBehavior::getVideoProgress).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        int resourceViews = behaviors.stream()
                .mapToInt(b -> b.getResourceViewCount() == null ? 0 : b.getResourceViewCount()).sum();
        int forumPosts = behaviors.stream()
                .mapToInt(b -> b.getForumPostCount() == null ? 0 : b.getForumPostCount()).sum();
        double submitRate = behaviors.isEmpty() ? 0.0
                : behaviors.stream().mapToInt(b -> b.getAssignmentSubmitCount() == null ? 0
                        : Math.min(1, b.getAssignmentSubmitCount())).average().orElse(0.0);

        double creditTotal = scores.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct()
                .map(courseById::get).filter(Objects::nonNull)
                .map(Course::getCredit).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).sum();

        return MlFeatureRow.builder()
                .studentId(stu.getId())
                .termId(termId)
                .avgScore(round(avg))
                .scoreTrend(round(avg - prev))
                .scoreStd(round(std))
                .minScore(round(minScore))
                .failCount(failCount)
                .failRate(round(failRate))
                .gradePointAvg(round(gpaAvg))
                .attendanceRate(round(attendanceRate))
                .lateCount(lateCount)
                .absentCount(absentCount)
                .avgParticipation(round(participation))
                .avgHomework(round(homework))
                .questionCount(questionCount)
                .studyMinutesAvg(round(studyMinutes))
                .videoProgressAvg(round(videoProgress))
                .resourceViewCount(resourceViews)
                .forumPostCount(forumPosts)
                .assignmentSubmitRate(round(submitRate))
                .creditTotal(round(creditTotal))
                .courseCount(scores.size())
                .build();
    }

    /**
     * 构建训练数据集：用第 n 学期特征预测第 n+1 学期是否出现不及格
     */
    public List<MlFeatureRow> buildTrainingDataset() {
        return buildTrainingDataset(true);
    }

    /**
     * 构建训练数据集（可指定是否并入外部开源样本，用于训练对比实验）
     */
    public List<MlFeatureRow> buildTrainingDataset(boolean includeExternal) {
        List<Term> terms = termMapper.selectList(null);
        terms.sort(Comparator.comparing(Term::getId));
        if (terms.size() < 2) {
            throw new BusinessException("至少需要两个学期的数据才能构建训练样本");
        }

        List<MlFeatureRow> dataset = new ArrayList<>();
        for (int i = 0; i < terms.size() - 1; i++) {
            Term current = terms.get(i);
            Term next = terms.get(i + 1);
            List<MlFeatureRow> features = buildFeatures(current.getId());
            Map<Long, Integer> failCountNextTerm = failCountByStudent(next.getId());
            for (MlFeatureRow row : features) {
                row.setLabel(failCountNextTerm.getOrDefault(row.getStudentId(), 0) > 0 ? 1 : 0);
                dataset.add(row);
            }
        }
        // 并入外部开源样本（天池/UCI/xAPI，import_external.py 生成；term_id<0 标识，来源留痕）
        if (includeExternal && externalDatasetLoader != null && externalDatasetLoader.exists()) {
            List<MlFeatureRow> external = externalDatasetLoader.load();
            if (!external.isEmpty()) {
                dataset.addAll(external);
                log.info("已并入外部开源训练样本 {} 条（source=EXTERNAL）", external.size());
            }
        }
        return dataset;
    }

    private Map<Long, Integer> failCountByStudent(Long termId) {
        List<Score> scores = scoreMapper.selectList(null);
        Map<Long, Integer> result = new HashMap<>();
        for (Score s : scores) {
            if (termId.equals(s.getTermId()) && s.getTotalScore() != null
                    && s.getTotalScore().compareTo(new BigDecimal("60")) < 0) {
                result.merge(s.getStudentId(), 1, Integer::sum);
            }
        }
        return result;
    }

    // ==================== 训练与预测 ====================

    /**
     * 训练模型
     */
    @Transactional(rollbackFor = Exception.class)
    public MlModel train(String modelType) {
        List<MlFeatureRow> dataset = buildTrainingDataset();
        MlDtos.TrainResponse res = mlServiceClient.train(modelType, dataset, 0.25);
        if (res == null) {
            throw new BusinessException("预测服务未返回训练结果");
        }

        MlModel model = new MlModel();
        model.setModelCode(modelType.toUpperCase() + "_" + res.getVersion());
        model.setModelName(modelType);
        model.setAlgorithm(modelType);
        model.setVersion(res.getVersion());
        model.setStatus("DEPLOYED");
        model.setTrainSampleCount(res.getSampleCount());
        model.setTrainTime(LocalDateTime.now());
        model.setModelPath(res.getModelPath());
        try {
            model.setMetricsJson(objectMapper.writeValueAsString(res.getMetrics()));
            model.setFeatureListJson(objectMapper.writeValueAsString(res.getFeatureImportance()));
        } catch (Exception e) {
            log.warn("序列化模型指标失败：{}", e.getMessage());
        }
        mlModelMapper.insert(model);
        log.info("模型训练并登记完成：{} v{} 样本数={}", modelType, res.getVersion(), res.getSampleCount());
        return model;
    }

    /**
     * 训练对比实验：同一模型、同一测试切分，对比"仅校内样本"与"校内+外部开源样本"的评估指标，
     * 量化外部数据对训练效果的贡献（直接回应答辩"数据源怎么训练"）。
     */
    public Map<String, Object> trainCompare(String modelType) {
        List<MlFeatureRow> baseline = buildTrainingDataset(false);
        List<MlFeatureRow> withExternal = buildTrainingDataset(true);
        MlDtos.TrainResponse base = mlServiceClient.train(modelType, baseline, 0.25);
        MlDtos.TrainResponse ext = mlServiceClient.train(modelType, withExternal, 0.25);
        if (base == null || ext == null) {
            throw new BusinessException("预测服务未返回训练结果");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("modelType", modelType);
        result.put("baseline", Map.of("sampleCount", base.getSampleCount() == null ? 0 : base.getSampleCount(),
                "metrics", base.getMetrics() == null ? Map.of() : base.getMetrics()));
        result.put("withExternal", Map.of("sampleCount", ext.getSampleCount() == null ? 0 : ext.getSampleCount(),
                "metrics", ext.getMetrics() == null ? Map.of() : ext.getMetrics()));

        Map<String, Object> delta = new java.util.LinkedHashMap<>();
        for (String k : List.of("accuracy", "precision", "recall", "f1", "roc_auc")) {
            double a = metricValue(base.getMetrics(), k);
            double b = metricValue(ext.getMetrics(), k);
            delta.put(k, Math.round((b - a) * 10000.0) / 10000.0);
        }
        result.put("delta", delta);
        int externalCount = (ext.getSampleCount() == null ? 0 : ext.getSampleCount())
                - (base.getSampleCount() == null ? 0 : base.getSampleCount());
        result.put("externalCount", externalCount);
        result.put("conclusion", buildCompareConclusion(delta));
        return result;
    }

    private double metricValue(Map<String, Object> metrics, String key) {
        if (metrics == null || !metrics.containsKey(key)) {
            return 0.0;
        }
        Object v = metrics.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private String buildCompareConclusion(Map<String, Object> delta) {
        double aucDelta = num(delta.get("roc_auc"));
        double f1Delta = num(delta.get("f1"));
        if (aucDelta > 0.01 || f1Delta > 0.01) {
            return "外部开源样本使模型泛化指标提升（AUC " + (aucDelta > 0 ? "+" + aucDelta : aucDelta)
                    + "，F1 " + (f1Delta > 0 ? "+" + f1Delta : f1Delta) + "），扩充训练样本对预测效果有正向贡献";
        }
        if (aucDelta < -0.01 || f1Delta < -0.01) {
            return "外部样本与校内学情存在分布差异，指标略有波动（AUC " + aucDelta + "，F1 " + f1Delta
                    + "），可按需通过 xqfx.ml.external-dataset 开关控制是否并入";
        }
        return "外部样本并入后模型指标基本持平（AUC " + aucDelta + "，F1 " + f1Delta
                + "），外部数据主要价值为扩充样本量与类别多样性";
    }

    private double num(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    /**
     * 对指定学期批量预测，写入预测结果与特征快照，并触发预警
     */
    @Transactional(rollbackFor = Exception.class)
    public int predict(Long termId, String modelType) {
        List<MlFeatureRow> rows = buildFeatures(termId);
        if (rows.isEmpty()) {
            throw new BusinessException("无可用预测数据");
        }

        MlModel model = latestModel(modelType);
        List<MlDtos.PredictItem> items = mlServiceClient.predict(modelType, rows);

        // 清除该学期的历史预测，避免重复累积
        predictionResultMapper.delete(new LambdaQueryWrapper<PredictionResult>()
                .eq(PredictionResult::getTermId, termId));

        List<PredictionResult> results = new ArrayList<>();
        Map<Long, MlFeatureRow> rowByStudent = rows.stream()
                .collect(Collectors.toMap(MlFeatureRow::getStudentId, r -> r, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();

        for (MlDtos.PredictItem item : items) {
            PredictionResult pr = new PredictionResult();
            pr.setStudentId(item.getStudentId());
            pr.setTermId(termId);
            pr.setModelId(model == null ? null : model.getId());
            pr.setRiskLevel(LEVEL_CODE.getOrDefault(item.getRiskLevel(), 1));
            pr.setRiskProbability(BigDecimal.valueOf(item.getRiskProbability())
                    .setScale(4, RoundingMode.HALF_UP));
            pr.setPredictedScore(predictScore(rowByStudent.get(item.getStudentId()), item));
            pr.setPredictedGpa(ScoreServiceImpl.calcGradePoint(pr.getPredictedScore()));
            pr.setExplainJson(buildExplain(item, rowByStudent.get(item.getStudentId())));
            // insertBatchSomeColumn 会写入全部列（含 null），NOT NULL 字段需显式赋值
            pr.setCreateTime(LocalDateTime.now());
            pr.setUpdateTime(LocalDateTime.now());
            pr.setIsDeleted(0);
            results.add(pr);
        }

        batchInsert(results, predictionResultMapper::insertBatchSomeColumn);
        saveFeatureSnapshot(rows, termId);

        int warningCount = warningService.generateFromPredictions(termId, results);
        log.info("预测完成：学期 {} 共 {} 条，触发预警 {} 条", termId, results.size(), warningCount);
        return results.size();
    }

    private MlModel latestModel(String modelType) {
        List<MlModel> models = mlModelMapper.selectList(new LambdaQueryWrapper<MlModel>()
                .eq(MlModel::getAlgorithm, modelType)
                .orderByDesc(MlModel::getId));
        return models.isEmpty() ? null : models.get(0);
    }

    /** 保存特征快照，保证预测可追溯 */
    private void saveFeatureSnapshot(List<MlFeatureRow> rows, Long termId) {
        featureRecordMapper.delete(new LambdaQueryWrapper<FeatureRecord>()
                .eq(FeatureRecord::getTermId, termId));
        List<FeatureRecord> records = new ArrayList<>();
        for (MlFeatureRow row : rows) {
            try {
                FeatureRecord fr = new FeatureRecord();
                fr.setStudentId(row.getStudentId());
                fr.setTermId(termId);
                fr.setFeatureJson(objectMapper.writeValueAsString(row));
                fr.setCreateTime(LocalDateTime.now());
                fr.setIsDeleted(0);
                records.add(fr);
            } catch (Exception e) {
                log.warn("序列化特征失败：{}", e.getMessage());
            }
        }
        if (!records.isEmpty()) {
            batchInsert(records, featureRecordMapper::insertBatchSomeColumn);
        }
    }

    /** 分批落库，避免单条 SQL 过长 */
    private <T> void batchInsert(List<T> list, java.util.function.Function<List<T>, Integer> inserter) {
        int size = 500;
        for (int i = 0; i < list.size(); i += size) {
            inserter.apply(list.subList(i, Math.min(i + size, list.size())));
        }
    }

    /** 基于当前平均成绩与风险偏移给出预测成绩 */
    private BigDecimal predictScore(MlFeatureRow row, MlDtos.PredictItem item) {
        if (row == null || row.getAvgScore() == null) {
            return BigDecimal.ZERO;
        }
        double adjusted = row.getAvgScore() - (item.getRiskProbability() - 0.5) * 10;
        adjusted = Math.max(0, Math.min(100, adjusted));
        return BigDecimal.valueOf(adjusted).setScale(2, RoundingMode.HALF_UP);
    }

    /** 生成可解释性说明：列出关键特征取值 */
    private String buildExplain(MlDtos.PredictItem item, MlFeatureRow row) {
        if (row == null) {
            return null;
        }
        try {
            Map<String, Object> explain = new java.util.LinkedHashMap<>();
            explain.put("riskLevel", item.getRiskLevel());
            explain.put("riskProbability", item.getRiskProbability());
            explain.put("mainFactors", List.of(
                    Map.of("feature", "avg_score", "value", row.getAvgScore()),
                    Map.of("feature", "fail_count", "value", row.getFailCount()),
                    Map.of("feature", "attendance_rate", "value", row.getAttendanceRate()),
                    Map.of("feature", "study_minutes_avg", "value", row.getStudyMinutesAvg()),
                    Map.of("feature", "assignment_submit_rate", "value", row.getAssignmentSubmitRate())
            ));
            return objectMapper.writeValueAsString(explain);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 工具方法 ====================

    private Long previousTermId(List<Term> sortedTerms, Long termId) {
        for (int i = 0; i < sortedTerms.size(); i++) {
            if (sortedTerms.get(i).getId().equals(termId) && i > 0) {
                return sortedTerms.get(i - 1).getId();
            }
        }
        return null;
    }

    private double avgScore(List<Score> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
    }

    private double stdDev(List<Score> scores) {
        if (scores == null || scores.size() < 2) {
            return 0.0;
        }
        double mean = avgScore(scores);
        double variance = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .map(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
