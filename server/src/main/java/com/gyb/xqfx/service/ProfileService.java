package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.OnlineBehavior;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.ProfileSnapshot;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentTag;
import com.gyb.xqfx.entity.TagDefinition;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ProfileSnapshotMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentTagMapper;
import com.gyb.xqfx.mapper.TagDefinitionMapper;
import com.gyb.xqfx.model.vo.BiasVO;
import com.gyb.xqfx.model.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学生画像服务：五维画像计算、自动打标、画像聚合
 *
 * <p>五维：学业水平 / 学习投入 / 行为表现 / 发展潜力 / 风险指数</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final AttendanceMapper attendanceMapper;
    private final OnlineBehaviorMapper onlineBehaviorMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final ProfileSnapshotMapper snapshotMapper;
    private final StudentTagMapper studentTagMapper;
    private final TagDefinitionMapper tagDefinitionMapper;
    private final ClazzMapper clazzMapper;
    private final CourseMapper courseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 采集学生的画像指标
     */
    public Map<String, Double> collectMetrics(Long studentId, Long termId) {
        Map<String, Double> m = new HashMap<>();

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId));
        double avg = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        long fail = scores.stream().filter(s -> s.getTotalScore() != null
                && s.getTotalScore().compareTo(new BigDecimal("60")) < 0).count();
        m.put("avg_score", avg);
        m.put("fail_count", (double) fail);
        m.put("course_count", (double) scores.size());

        List<Attendance> attendances = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStudentId, studentId)
                .eq(termId != null, Attendance::getTermId, termId));
        long absent = attendances.stream()
                .filter(a -> "ABSENT".equals(a.getStatus()) || "EARLY".equals(a.getStatus())).count();
        double attendanceRate = attendances.isEmpty() ? 1.0 : 1.0 - (double) absent / attendances.size();
        m.put("attendance_rate", attendanceRate);
        m.put("absent_count", (double) absent);

        List<OnlineBehavior> behaviors = onlineBehaviorMapper.selectList(new LambdaQueryWrapper<OnlineBehavior>()
                .eq(OnlineBehavior::getStudentId, studentId)
                .eq(termId != null, OnlineBehavior::getTermId, termId));
        m.put("study_minutes", (double) behaviors.stream()
                .mapToInt(b -> b.getStudyMinutes() == null ? 0 : b.getStudyMinutes()).sum());
        m.put("video_progress", behaviors.stream().map(OnlineBehavior::getVideoProgress)
                .filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0.0));
        m.put("assignment_submit_rate", behaviors.isEmpty() ? 0.0 : behaviors.stream()
                .mapToInt(b -> b.getAssignmentSubmitCount() == null ? 0
                        : Math.min(1, b.getAssignmentSubmitCount())).average().orElse(0.0));

        List<PredictionResult> preds = predictionResultMapper.selectList(
                new LambdaQueryWrapper<PredictionResult>().eq(PredictionResult::getStudentId, studentId)
                        .eq(termId != null, PredictionResult::getTermId, termId));
        m.put("risk_probability", preds.isEmpty() ? 0.0
                : preds.get(0).getRiskProbability() == null ? 0.0
                : preds.get(0).getRiskProbability().doubleValue());
        return m;
    }

    /**
     * 计算五维画像分（各维 0-100）
     */
    public Map<String, BigDecimal> computeDimensions(Map<String, Double> m) {
        Map<String, BigDecimal> dims = new LinkedHashMap<>();

        // 学业水平：平均成绩为主，不及格扣分
        double academic = m.getOrDefault("avg_score", 0.0) - m.getOrDefault("fail_count", 0.0) * 3;
        dims.put("academic", clamp(academic));

        // 学习投入：出勤 50% + 视频完成度 20% + 作业提交率 30%
        double engagement = m.getOrDefault("attendance_rate", 0.0) * 50
                + m.getOrDefault("video_progress", 0.0) * 0.2
                + m.getOrDefault("assignment_submit_rate", 0.0) * 30;
        dims.put("engagement", clamp(engagement));

        // 行为表现：出勤为主，缺勤扣分
        double behavior = m.getOrDefault("attendance_rate", 0.0) * 100
                - m.getOrDefault("absent_count", 0.0) * 2;
        dims.put("behavior", clamp(behavior));

        // 发展潜力：学业与投入的综合，体现上升空间
        double potential = dims.get("academic").doubleValue() * 0.6
                + dims.get("engagement").doubleValue() * 0.4;
        dims.put("potential", clamp(potential));

        // 风险指数：模型概率为主，叠加不及格门数
        double risk = m.getOrDefault("risk_probability", 0.0) * 100
                + m.getOrDefault("fail_count", 0.0) * 5;
        dims.put("risk", clamp(risk));

        return dims;
    }

    /**
     * 生成并保存画像快照，同时执行自动打标
     */
    public ProfileSnapshot generate(Long studentId, Long termId) {
        Map<String, Double> metrics = collectMetrics(studentId, termId);
        Map<String, BigDecimal> dims = computeDimensions(metrics);

        ProfileSnapshot snapshot = new ProfileSnapshot();
        snapshot.setStudentId(studentId);
        snapshot.setTermId(termId);
        snapshot.setAcademicScore(dims.get("academic"));
        snapshot.setEngagementScore(dims.get("engagement"));
        snapshot.setBehaviorScore(dims.get("behavior"));
        snapshot.setPotentialScore(dims.get("potential"));
        snapshot.setRiskScore(dims.get("risk"));
        snapshot.setSnapshotDate(LocalDate.now());
        try {
            Map<String, Object> detail = new LinkedHashMap<>(dims);
            detail.put("metrics", metrics);
            snapshot.setDimensionJson(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            log.warn("序列化画像明细失败：{}", e.getMessage());
        }
        snapshotMapper.insert(snapshot);

        autoTag(studentId, termId, metrics);
        return snapshot;
    }

    /**
     * 按标签定义自动打标
     *
     * @return 命中标签数
     */
    public int autoTag(Long studentId, Long termId, Map<String, Double> metrics) {
        List<TagDefinition> tags = tagDefinitionMapper.selectList(
                new LambdaQueryWrapper<TagDefinition>().eq(TagDefinition::getIsAuto, 1));
        int hit = 0;
        for (TagDefinition tag : tags) {
            if (!matchRule(tag.getRuleJson(), metrics)) {
                continue;
            }
            // 已有同一标签则跳过，避免重复
            long exists = studentTagMapper.selectCount(new LambdaQueryWrapper<StudentTag>()
                    .eq(StudentTag::getStudentId, studentId)
                    .eq(StudentTag::getTagId, tag.getId()));
            if (exists > 0) {
                continue;
            }
            StudentTag st = new StudentTag();
            st.setStudentId(studentId);
            st.setTagId(tag.getId());
            st.setTermId(termId);
            st.setTagValue(tag.getTagName());
            st.setSource("AUTO");
            st.setEffectiveDate(LocalDate.now());
            studentTagMapper.insert(st);
            hit++;
        }
        return hit;
    }

    /**
     * 标签规则匹配：ruleJson 形如 {"avg_score":{"lt":60},"fail_count":{"gte":2}}
     * 支持 gte / gt / lte / lt / eq 五种操作符；多个条件需全部满足。
     */
    public boolean matchRule(String ruleJson, Map<String, Double> metrics) {
        if (ruleJson == null || ruleJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(ruleJson);
            if (root.isEmpty()) {
                // 空规则不命中，避免无规则标签被批量打上
                return false;
            }
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String field = entry.getKey();
                JsonNode cond = entry.getValue();
                double actual = metrics.getOrDefault(field, 0.0);
                var ops = cond.fields();
                while (ops.hasNext()) {
                    var op = ops.next();
                    double threshold = op.getValue().asDouble();
                    boolean ok = switch (op.getKey()) {
                        case "gte" -> actual >= threshold;
                        case "gt" -> actual > threshold;
                        case "lte" -> actual <= threshold;
                        case "lt" -> actual < threshold;
                        case "eq" -> Double.compare(actual, threshold) == 0;
                        default -> false;
                    };
                    if (!ok) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("标签规则解析失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询学生画像（含五维、标签、历史趋势）
     */
    public ProfileVO getProfile(Long studentId, Long termId) {
        ProfileVO vo = new ProfileVO();
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在：" + studentId);
        }
        vo.setStudentId(studentId);
        vo.setStudentNo(student.getStudentNo());
        vo.setStudentName(student.getName());
        if (student.getClazzId() != null) {
            var clazz = clazzMapper.selectById(student.getClazzId());
            if (clazz != null) {
                vo.setClazzName(clazz.getClazzName());
            }
        }

        List<ProfileSnapshot> history = snapshotMapper.selectList(
                new LambdaQueryWrapper<ProfileSnapshot>()
                        .eq(ProfileSnapshot::getStudentId, studentId)
                        .orderByAsc(ProfileSnapshot::getId));
        ProfileSnapshot latest = history.isEmpty() ? null : history.get(history.size() - 1);
        if (latest == null) {
            latest = generate(studentId, termId);
            history = List.of(latest);
        }
        vo.setAcademicScore(latest.getAcademicScore());
        vo.setEngagementScore(latest.getEngagementScore());
        vo.setBehaviorScore(latest.getBehaviorScore());
        vo.setPotentialScore(latest.getPotentialScore());
        vo.setRiskScore(latest.getRiskScore());
        vo.setSnapshotDate(latest.getSnapshotDate());
        vo.setDimensionJson(latest.getDimensionJson());

        Map<String, List<Object>> trend = new LinkedHashMap<>();
        trend.put("academic", history.stream().map(ProfileSnapshot::getAcademicScore).map(v -> (Object) v).toList());
        trend.put("engagement", history.stream().map(ProfileSnapshot::getEngagementScore).map(v -> (Object) v).toList());
        trend.put("behavior", history.stream().map(ProfileSnapshot::getBehaviorScore).map(v -> (Object) v).toList());
        trend.put("risk", history.stream().map(ProfileSnapshot::getRiskScore).map(v -> (Object) v).toList());
        vo.setTrend(trend);

        List<StudentTag> tags = studentTagMapper.selectList(
                new LambdaQueryWrapper<StudentTag>().eq(StudentTag::getStudentId, studentId));
        List<Long> tagIds = tags.stream().map(StudentTag::getTagId).filter(Objects::nonNull).distinct().toList();
        Map<Long, TagDefinition> defs = tagIds.isEmpty() ? Map.of()
                : tagDefinitionMapper.selectBatchIds(tagIds).stream()
                .collect(java.util.stream.Collectors.toMap(TagDefinition::getId, t -> t, (a, b) -> a));

        List<ProfileVO.TagItem> items = new ArrayList<>();
        for (StudentTag t : tags) {
            TagDefinition def = defs.get(t.getTagId());
            if (def == null) {
                continue;
            }
            ProfileVO.TagItem item = new ProfileVO.TagItem();
            item.setTagCode(def.getTagCode());
            item.setTagName(def.getTagName());
            item.setCategory(def.getTagCategory());
            item.setColor(def.getColor());
            item.setSource(t.getSource());
            items.add(item);
        }
        vo.setTags(items);
        return vo;
    }

    /**
     * 画像快照历史（成长档案时间轴数据源）
     */
    public List<ProfileSnapshot> snapshots(Long studentId) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<ProfileSnapshot>()
                .eq(ProfileSnapshot::getStudentId, studentId)
                .orderByAsc(ProfileSnapshot::getSnapshotDate)
                .orderByAsc(ProfileSnapshot::getId));
    }

    /**
     * 偏科分析：学生各课程均分 vs 对比基准（班级优先，其次专业，最后全校）
     *
     * <p>判定规则：高于基准 10 分及以上为强科（STRONG），低于基准 10 分及以上为弱科（WEAK），
     * 其余为均衡（NORMAL）。偏科指数 = 弱科占比 × 平均弱科差距，反映偏科严重程度。</p>
     */
    public BiasVO biasAnalysis(Long studentId, Long termId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("学生不存在：" + studentId);
        }
        BiasVO vo = new BiasVO();
        vo.setStudentId(studentId);
        vo.setStudentName(student.getName());
        if (student.getClazzId() != null) {
            var clazz = clazzMapper.selectById(student.getClazzId());
            if (clazz != null) {
                vo.setClazzName(clazz.getClazzName());
            }
        }

        // 该生成绩按课程聚合
        List<Score> myScores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore)
                .isNotNull(Score::getCourseId));
        Map<Long, List<BigDecimal>> myByCourse = myScores.stream().collect(
                java.util.stream.Collectors.groupingBy(Score::getCourseId,
                        java.util.stream.Collectors.mapping(Score::getTotalScore,
                                java.util.stream.Collectors.toList())));
        if (myByCourse.isEmpty()) {
            vo.setItems(List.of());
            vo.setStrongCount(0);
            vo.setWeakCount(0);
            vo.setTotal(0);
            vo.setBiasIndex(BigDecimal.ZERO);
            return vo;
        }

        // 对比群体：班级优先，班级无数据回退专业，再回退全校
        List<Student> peers = new ArrayList<>(studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClazzId, student.getClazzId())
                .ne(Student::getId, studentId)));
        String baseline = "CLAZZ";
        if (peers.isEmpty()) {
            peers = new ArrayList<>(studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .eq(Student::getMajorId, student.getMajorId())
                    .ne(Student::getId, studentId)));
            baseline = "MAJOR";
        }
        if (peers.isEmpty()) {
            peers = new ArrayList<>(studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .ne(Student::getId, studentId)));
            baseline = "ALL";
        }
        vo.setBaseline(baseline);
        List<Long> peerIds = peers.stream().map(Student::getId).toList();

        // 对比群体成绩按课程聚合
        Map<Long, List<BigDecimal>> peerByCourse = peerIds.isEmpty() ? Map.of()
                : scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .in(Score::getStudentId, peerIds)
                        .eq(termId != null, Score::getTermId, termId)
                        .isNotNull(Score::getTotalScore)
                        .isNotNull(Score::getCourseId)).stream().collect(
                        java.util.stream.Collectors.groupingBy(Score::getCourseId,
                                java.util.stream.Collectors.mapping(Score::getTotalScore,
                                        java.util.stream.Collectors.toList())));

        List<BiasVO.BiasItem> items = new ArrayList<>();
        int strong = 0;
        int weak = 0;
        BigDecimal weakDiffSum = BigDecimal.ZERO;
        for (Map.Entry<Long, List<BigDecimal>> e : myByCourse.entrySet()) {
            Long courseId = e.getKey();
            BigDecimal studentAvg = avg(e.getValue());
            Course course = courseMapper.selectById(courseId);
            List<BigDecimal> peerScores = peerByCourse.getOrDefault(courseId, List.of());

            BiasVO.BiasItem item = new BiasVO.BiasItem();
            item.setCourseId(courseId);
            item.setCourseName(course == null ? "课程" + courseId : course.getCourseName());
            item.setCourseType(course == null ? null : course.getCourseType());
            item.setStudentAvg(studentAvg);
            item.setRankPercent(peerScores.isEmpty() ? null : percentile(studentAvg, peerScores));

            BigDecimal baselineAvg = peerScores.isEmpty() ? studentAvg : avg(peerScores);
            item.setBaselineAvg(baselineAvg);
            BigDecimal diff = studentAvg.subtract(baselineAvg).setScale(2, RoundingMode.HALF_UP);
            item.setDiff(diff);

            if (diff.compareTo(new BigDecimal("10")) >= 0) {
                item.setVerdict("STRONG");
                item.setAdvice("优势科目，可保持并争取更高目标，或作为学习方法输出的参考");
                strong++;
            } else if (diff.compareTo(new BigDecimal("-10")) <= 0) {
                item.setVerdict("WEAK");
                item.setAdvice("薄弱科目，建议重点复习、参加答疑，必要时纳入学业预警与帮扶");
                weak++;
                weakDiffSum = weakDiffSum.add(diff.abs());
            } else {
                item.setVerdict("NORMAL");
                item.setAdvice("与群体水平相当，保持稳定即可");
            }
            items.add(item);
        }

        // 弱科优先展示
        items.sort(Comparator.comparing(BiasVO.BiasItem::getVerdict,
                Comparator.comparingInt(v -> "WEAK".equals(v) ? 0 : ("STRONG".equals(v) ? 1 : 2))));

        BigDecimal weakRatio = items.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(weak).divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgWeakDiff = weak == 0 ? BigDecimal.ZERO
                : weakDiffSum.divide(BigDecimal.valueOf(weak), 2, RoundingMode.HALF_UP);

        vo.setItems(items);
        vo.setStrongCount(strong);
        vo.setWeakCount(weak);
        vo.setTotal(items.size());
        vo.setBiasIndex(weakRatio.multiply(avgWeakDiff).setScale(2, RoundingMode.HALF_UP));
        return vo;
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /** 该生成绩在对比群体中的百分位（0-100） */
    private int percentile(BigDecimal value, List<BigDecimal> peerScores) {
        long below = peerScores.stream().filter(Objects::nonNull)
                .filter(s -> s.compareTo(value) < 0).count();
        return (int) Math.round(below * 100.0 / peerScores.size());
    }

    /** 约束到 0-100 并保留两位小数 */
    private BigDecimal clamp(double v) {
        return scale(Math.max(0, Math.min(100, v)));
    }

    private BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
