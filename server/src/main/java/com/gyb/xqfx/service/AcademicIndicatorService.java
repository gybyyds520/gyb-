package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.Term;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.TermMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学业核心指标计算服务
 *
 * <p>对标高校学业预警实践，提供四类可解释的量化指标：
 * <ul>
 *   <li>学分绩点（GPA）：按课程学分加权，4.0 制</li>
 *   <li>学分获取情况：已获学分与欠学分（不及格课程学分之和）</li>
 *   <li>成绩趋势斜率：学期均分最小二乘回归斜率，负值表示下滑</li>
 *   <li>绩点偏离度：与同班级均值的标准差倍数，识别"低空飞过"的隐性风险</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicIndicatorService {

    /** 默认学分（课程未配置学分时按此值计） */
    private static final BigDecimal DEFAULT_CREDIT = new BigDecimal("3.0");

    /** 欠学分警戒线：累计不及格学分达到该值判为风险 */
    private static final BigDecimal DEFICIT_RISK = new BigDecimal("8.0");

    /** 欠学分关注线 */
    private static final BigDecimal DEFICIT_ATTENTION = new BigDecimal("4.0");

    /** 趋势斜率风险线：每学期均分下滑超过该分值 */
    private static final BigDecimal SLOPE_RISK = new BigDecimal("-8.0");

    /** 趋势斜率关注线 */
    private static final BigDecimal SLOPE_ATTENTION = new BigDecimal("-4.0");

    /** 绩点偏离度风险线（低于均值 N 个标准差） */
    private static final BigDecimal DEVIATION_RISK = new BigDecimal("-1.0");

    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final TermMapper termMapper;
    private final DataScopeService dataScopeService;

    /**
     * 百分制成绩换算为绩点（4.0 制）
     */
    public BigDecimal gradePoint(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        double s = score.doubleValue();
        if (s >= 90) {
            return new BigDecimal("4.0");
        }
        if (s >= 85) {
            return new BigDecimal("3.7");
        }
        if (s >= 82) {
            return new BigDecimal("3.3");
        }
        if (s >= 78) {
            return new BigDecimal("3.0");
        }
        if (s >= 75) {
            return new BigDecimal("2.7");
        }
        if (s >= 72) {
            return new BigDecimal("2.3");
        }
        if (s >= 68) {
            return new BigDecimal("2.0");
        }
        if (s >= 64) {
            return new BigDecimal("1.5");
        }
        if (s >= 60) {
            return new BigDecimal("1.0");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算学生学业指标
     *
     * @param termId 为空表示累计（跨全部学期）
     */
    public AcademicIndicatorVO calculate(Long studentId, Long termId) {
        AcademicIndicatorVO vo = new AcademicIndicatorVO();
        vo.setStudentId(studentId);
        Student student = studentMapper.selectById(studentId);
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
        }
        vo.setTermId(termId);
        if (termId != null) {
            Term term = termMapper.selectById(termId);
            if (term != null) {
                vo.setTermName(term.getTermName());
            }
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        if (scores.isEmpty()) {
            vo.setCourseCount(0);
            vo.setFailCount(0);
            vo.setTotalCredit(BigDecimal.ZERO);
            vo.setEarnedCredit(BigDecimal.ZERO);
            vo.setDeficitCredit(BigDecimal.ZERO);
            vo.setGpa(BigDecimal.ZERO);
            vo.setAvgScoreSeries(List.of());
            vo.setTrendSlope(BigDecimal.ZERO);
            vo.setDeviation(BigDecimal.ZERO);
            vo.setRiskFlag("NORMAL");
            vo.setReason("无成绩数据");
            return vo;
        }

        // 学分与绩点
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal deficit = BigDecimal.ZERO;
        int failCount = 0;
        for (Score sc : scores) {
            BigDecimal credit = creditOf(sc.getCourseId());
            BigDecimal point = gradePoint(sc.getTotalScore());
            weighted = weighted.add(point.multiply(credit));
            totalCredit = totalCredit.add(credit);
            boolean pass = sc.getIsPass() != null ? sc.getIsPass() == 1
                    : sc.getTotalScore().compareTo(new BigDecimal("60")) >= 0;
            if (pass) {
                earned = earned.add(credit);
            } else {
                deficit = deficit.add(credit);
                failCount++;
            }
        }
        vo.setCourseCount(scores.size());
        vo.setTotalCredit(scale2(totalCredit));
        vo.setEarnedCredit(scale2(earned));
        vo.setDeficitCredit(scale2(deficit));
        vo.setFailCount(failCount);
        vo.setGpa(totalCredit.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO : weighted.divide(totalCredit, 2, RoundingMode.HALF_UP));

        // 趋势：各学期均分序列的回归斜率
        List<BigDecimal> series = termAvgSeries(studentId);
        vo.setAvgScoreSeries(series);
        vo.setTrendSlope(linearSlope(series));

        // 绩点偏离度（对比同班级）
        vo.setDeviation(deviation(studentId, termId, vo.getGpa()));

        // 综合判定
        vo.setRiskFlag(evalRiskFlag(vo));
        vo.setReason(buildReason(vo));
        return vo;
    }

    /**
     * 批量计算某学期全体学生指标
     */
    public List<AcademicIndicatorVO> batchCalculate(Long termId, int limit) {
        List<Student> students = studentMapper.selectList(null);
        // 行级数据范围：教师只看所带班级/授课班，院级只看本院，学生与家长不进入批量接口
        List<Long> visible = visibleStudentIds();
        if (visible != null) {
            students = students.stream().filter(s -> visible.contains(s.getId())).toList();
        }
        List<AcademicIndicatorVO> list = new ArrayList<>();
        for (Student s : students) {
            list.add(calculate(s.getId(), termId));
        }
        // 风险优先：欠学分多、GPA 低者排前
        list.sort(Comparator
                .comparing(AcademicIndicatorVO::getDeficitCredit).reversed()
                .thenComparing(AcademicIndicatorVO::getGpa));
        return limit > 0 ? list.stream().limit(limit).toList() : list;
    }

    /**
     * 学生各学期均分序列（按学期顺序）
     */
    public List<BigDecimal> termAvgSeries(Long studentId) {
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .isNotNull(Score::getTotalScore)
                .isNotNull(Score::getTermId));
        Map<Long, List<BigDecimal>> byTerm = new LinkedHashMap<>();
        for (Score sc : scores) {
            byTerm.computeIfAbsent(sc.getTermId(), k -> new ArrayList<>()).add(sc.getTotalScore());
        }
        return byTerm.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> mean(e.getValue()))
                .toList();
    }

    /**
     * 最小二乘线性回归斜率：正值上升，负值下滑
     */
    public BigDecimal linearSlope(List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return BigDecimal.ZERO;
        }
        int n = values.size();
        double meanX = (n - 1) / 2.0;
        double meanY = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = i - meanX;
            numerator += dx * (values.get(i).doubleValue() - meanY);
            denominator += dx * dx;
        }
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator / denominator).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 绩点偏离度：与同班级学生 GPA 均值相差的标准差倍数
     */
    public BigDecimal deviation(Long studentId, Long termId, BigDecimal gpa) {
        if (gpa == null) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> peerGpas = peerGpas(studentId, termId);
        if (peerGpas.size() < 2) {
            return BigDecimal.ZERO;
        }
        double mean = peerGpas.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        double variance = peerGpas.stream().mapToDouble(v -> {
            double d = v.doubleValue() - mean;
            return d * d;
        }).average().orElse(0.0);
        double std = Math.sqrt(variance);
        if (std < 1e-9) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((gpa.doubleValue() - mean) / std).setScale(2, RoundingMode.HALF_UP);
    }

    /** 同班级（排除本人）学生的 GPA */
    private List<BigDecimal> peerGpas(Long studentId, Long termId) {
        Student me = studentMapper.selectById(studentId);
        Long clazzId = me == null ? null : me.getClazzId();
        List<Student> peers = clazzId == null
                ? studentMapper.selectList(null)
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().eq(Student::getClazzId, clazzId));
        List<BigDecimal> result = new ArrayList<>();
        for (Student p : peers) {
            if (Objects.equals(p.getId(), studentId)) {
                continue;
            }
            List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentId, p.getId())
                    .eq(termId != null, Score::getTermId, termId)
                    .isNotNull(Score::getTotalScore));
            if (scores.isEmpty()) {
                continue;
            }
            BigDecimal weighted = BigDecimal.ZERO;
            BigDecimal creditSum = BigDecimal.ZERO;
            for (Score sc : scores) {
                BigDecimal credit = creditOf(sc.getCourseId());
                weighted = weighted.add(gradePoint(sc.getTotalScore()).multiply(credit));
                creditSum = creditSum.add(credit);
            }
            if (creditSum.compareTo(BigDecimal.ZERO) > 0) {
                result.add(weighted.divide(creditSum, 2, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    /**
     * 综合风险判定：欠学分、趋势下滑、绩点偏离任一触及风险线即为风险
     */
    public String evalRiskFlag(AcademicIndicatorVO vo) {
        if (vo.getDeficitCredit() != null && vo.getDeficitCredit().compareTo(DEFICIT_RISK) >= 0) {
            return "RISK";
        }
        if (vo.getTrendSlope() != null && vo.getTrendSlope().compareTo(SLOPE_RISK) <= 0) {
            return "RISK";
        }
        if (vo.getDeviation() != null && vo.getDeviation().compareTo(DEVIATION_RISK) <= 0
                && vo.getGpa() != null && vo.getGpa().compareTo(new BigDecimal("2.0")) < 0) {
            return "RISK";
        }
        if (vo.getDeficitCredit() != null && vo.getDeficitCredit().compareTo(DEFICIT_ATTENTION) >= 0) {
            return "ATTENTION";
        }
        if (vo.getTrendSlope() != null && vo.getTrendSlope().compareTo(SLOPE_ATTENTION) <= 0) {
            return "ATTENTION";
        }
        return "NORMAL";
    }

    /** 生成可解释的判定依据，供辅导员直接用于谈话 */
    public String buildReason(AcademicIndicatorVO vo) {
        StringBuilder sb = new StringBuilder();
        if (vo.getDeficitCredit() != null && vo.getDeficitCredit().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("累计欠学分 ").append(vo.getDeficitCredit())
                    .append("（不及格 ").append(vo.getFailCount()).append(" 门）；");
        }
        if (vo.getTrendSlope() != null && vo.getTrendSlope().compareTo(BigDecimal.ZERO) < 0) {
            sb.append("成绩呈下滑趋势（斜率 ").append(vo.getTrendSlope()).append(" 分/学期）；");
        }
        if (vo.getDeviation() != null && vo.getDeviation().compareTo(DEVIATION_RISK) <= 0) {
            sb.append("绩点显著低于班级平均水平（偏离 ").append(vo.getDeviation()).append(" 个标准差）；");
        }
        if (sb.length() == 0) {
            sb.append("各项学业指标正常");
        }
        return sb.toString();
    }

    /** 当前登录用户可见的学生ID，null 表示不限（全量或非 Web 调用） */
    private List<Long> visibleStudentIds() {
        Long userId = com.gyb.xqfx.security.SecurityUtils.currentUserIdOrNull();
        return userId == null ? null : dataScopeService.visibleStudentIds(userId);
    }

    private BigDecimal creditOf(Long courseId) {
        if (courseId == null) {
            return DEFAULT_CREDIT;
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getCredit() == null || course.getCredit().compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_CREDIT;
        }
        return course.getCredit();
    }

    private BigDecimal mean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
