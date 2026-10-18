package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Course;
import com.gyb.xqfx.entity.LearningGoal;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.CourseMapper;
import com.gyb.xqfx.mapper.LearningGoalMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.vo.BiasVO;
import com.gyb.xqfx.model.vo.GoalVO;
import com.gyb.xqfx.model.vo.ResourceRecommendVO;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习目标与资源推荐服务
 *
 * <p>学生自我设定学习目标（均分/绩点/单科），系统按成绩实时计算当前值与差距；
 * 资源推荐基于偏科分析与目标差距规则化生成，教师可查看学生目标并提供指导。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final LearningGoalMapper goalMapper;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final AcademicIndicatorService indicatorService;
    private final ProfileService profileService;
    private final DataScopeService dataScopeService;

    /** 当前登录学生ID；非学生返回 null */
    private Long myStudentId() {
        Long studentId = SecurityUtils.currentStudentId();
        if (studentId == null) {
            studentId = dataScopeService.currentStudentId();
        }
        return studentId;
    }

    /** 解析目标所属学生：学生本人或教师指定（含数据范围校验） */
    private Long resolveStudentId(Long studentId) {
        Long mine = myStudentId();
        if (mine != null) {
            return mine;
        }
        if (studentId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请指定学生");
        }
        dataScopeService.requireStudentAccess(studentId);
        return studentId;
    }

    /**
     * 目标列表（含当前值/差距/进度）
     */
    public List<GoalVO> list(Long studentId, Long termId) {
        Long sid = resolveStudentId(studentId);
        List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getStudentId, sid)
                .eq(termId != null, LearningGoal::getTermId, termId)
                .orderByDesc(LearningGoal::getCreateTime));
        if (goals.isEmpty()) {
            return List.of();
        }
        Student student = studentMapper.selectById(sid);
        Map<Long, Course> courseMap = courseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<Long, List<Score>> myScores = scoreMapper.selectList(
                        new LambdaQueryWrapper<Score>().eq(Score::getStudentId, sid))
                .stream().collect(Collectors.groupingBy(Score::getCourseId));
        BigDecimal avgAll = avgScoreOf(myScores.values().stream()
                .flatMap(List::stream).toList());
        BigDecimal gpaAll = indicatorService.calculate(sid, null).getGpa();

        List<GoalVO> result = new ArrayList<>();
        for (LearningGoal g : goals) {
            GoalVO vo = new GoalVO();
            vo.setId(g.getId());
            vo.setStudentId(sid);
            if (student != null) {
                vo.setStudentNo(student.getStudentNo());
                vo.setStudentName(student.getName());
            }
            vo.setTermId(g.getTermId());
            vo.setGoalType(g.getGoalType());
            vo.setGoalTypeText(goalTypeText(g.getGoalType()));
            vo.setTargetValue(g.getTargetValue());
            vo.setCourseId(g.getCourseId());
            Course c = g.getCourseId() == null ? null : courseMap.get(g.getCourseId());
            vo.setCourseName(c == null ? null : c.getCourseName());
            vo.setStatus(g.getStatus());
            vo.setStatusText(statusText(g.getStatus()));
            vo.setDeadline(g.getDeadline());
            vo.setNote(g.getNote());
            vo.setCreateTime(g.getCreateTime());

            BigDecimal current = currentValue(sid, g, myScores, avgAll, gpaAll);
            vo.setCurrentValue(current);
            BigDecimal gap = g.getTargetValue() == null ? BigDecimal.ZERO
                    : g.getTargetValue().subtract(current).setScale(2, RoundingMode.HALF_UP);
            vo.setGap(gap);
            vo.setProgress("ACHIEVED".equals(g.getStatus()) ? 100
                    : progressOf(current, g.getTargetValue()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 保存目标（新建或更新）
     */
    @Transactional
    public Long save(Long studentId, LearningGoal goal) {
        Long sid = resolveStudentId(studentId);
        goal.setStudentId(sid);
        if (goal.getGoalType() == null || goal.getTargetValue() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标类型与目标值必填");
        }
        if (goal.getId() == null) {
            goal.setStatus(goal.getStatus() == null ? "ACTIVE" : goal.getStatus());
            goalMapper.insert(goal);
        } else {
            goalMapper.updateById(goal);
        }
        return goal.getId();
    }

    /**
     * 更新目标状态（达成/放弃/恢复进行中）
     */
    @Transactional
    public boolean updateStatus(Long id, String status) {
        LearningGoal goal = goalMapper.selectById(id);
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标不存在");
        }
        resolveStudentId(goal.getStudentId());
        goal.setStatus(status);
        return goalMapper.updateById(goal) > 0;
    }

    /**
     * 删除目标
     */
    @Transactional
    public boolean delete(Long id) {
        LearningGoal goal = goalMapper.selectById(id);
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标不存在");
        }
        resolveStudentId(goal.getStudentId());
        return goalMapper.deleteById(id) > 0;
    }

    /**
     * 学习资源推荐：基于偏科弱科 + 目标差距规则化生成
     */
    public List<ResourceRecommendVO> recommendations(Long studentId, Long termId) {
        Long sid = resolveStudentId(studentId);
        List<ResourceRecommendVO> items = new ArrayList<>();

        // 1) 偏科弱科驱动
        BiasVO bias = profileService.biasAnalysis(sid, termId);
        if (bias.getItems() != null) {
            for (BiasVO.BiasItem item : bias.getItems()) {
                if ("WEAK".equals(item.getVerdict())) {
                    items.addAll(buildWeakCourseRecommend(item));
                }
            }
        }

        // 2) 目标差距驱动（未达成的进行中目标）
        List<LearningGoal> active = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getStudentId, sid)
                .eq(LearningGoal::getStatus, "ACTIVE"));
        for (LearningGoal g : active) {
            GoalVO gv = list(sid, null).stream().filter(x -> x.getId().equals(g.getId()))
                    .findFirst().orElse(null);
            if (gv == null) {
                continue;
            }
            if (gv.getGap() != null && gv.getGap().compareTo(BigDecimal.ZERO) > 0) {
                items.add(buildGapRecommend(gv));
            }
        }

        // 去重：同一课程同一类型只保留一条
        Map<String, ResourceRecommendVO> dedup = items.stream().collect(Collectors.toMap(
                r -> (r.getCourseId() == null ? "ALL" : r.getCourseId()) + "|" + r.getResourceType(),
                Function.identity(), (a, b) -> a.getPriority() <= b.getPriority() ? a : b,
                java.util.LinkedHashMap::new));
        return dedup.values().stream()
                .sorted(Comparator.comparingInt(ResourceRecommendVO::getPriority))
                .toList();
    }

    // ==================== 内部工具 ====================

    private BigDecimal currentValue(Long sid, LearningGoal g,
                                    Map<Long, List<Score>> myScores,
                                    BigDecimal avgAll, BigDecimal gpaAll) {
        if ("GPA".equals(g.getGoalType())) {
            return gpaAll;
        }
        if ("COURSE".equals(g.getGoalType()) && g.getCourseId() != null) {
            List<Score> sc = myScores.getOrDefault(g.getCourseId(), List.of());
            return avgScoreOf(sc);
        }
        if (g.getTermId() != null) {
            List<Score> sc = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentId, sid).eq(Score::getTermId, g.getTermId()));
            return avgScoreOf(sc);
        }
        return avgAll;
    }

    private BigDecimal avgScoreOf(List<Score> scores) {
        List<BigDecimal> vals = scores.stream().map(Score::getTotalScore)
                .filter(Objects::nonNull).toList();
        if (vals.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private int progressOf(BigDecimal current, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (current.compareTo(target) >= 0) {
            return 100;
        }
        int p = current.multiply(BigDecimal.valueOf(100))
                .divide(target, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, p));
    }

    private List<ResourceRecommendVO> buildWeakCourseRecommend(BiasVO.BiasItem item) {
        List<ResourceRecommendVO> list = new ArrayList<>();
        BigDecimal gap = item.getDiff() == null ? BigDecimal.ZERO : item.getDiff().abs();
        String course = item.getCourseName() == null ? "该课程" : item.getCourseName();

        ResourceRecommendVO video = new ResourceRecommendVO();
        video.setResourceType("VIDEO");
        video.setResourceTypeText("视频课程");
        video.setTitle("「" + course + "」系统复习视频");
        video.setReason(String.format("本课程低于班级均分 %.1f 分（百分位 %d%%），属于明显薄弱科目", gap, item.getRankPercent() == null ? 0 : item.getRankPercent()));
        video.setAction("建议按章节回看教学视频并整理课堂笔记，每周完成 2 次复习");
        video.setCourseId(item.getCourseId());
        video.setCourseName(course);
        video.setCurrentScore(item.getStudentAvg());
        video.setGap(gap.negate());
        video.setPriority(1);
        list.add(video);

        ResourceRecommendVO practice = new ResourceRecommendVO();
        practice.setResourceType("PRACTICE");
        practice.setResourceTypeText("专项练习");
        practice.setTitle("「" + course + "」薄弱知识点专项练习");
        practice.setReason("针对低于及格线的知识点做错题归类，优先补齐高频失分点");
        practice.setAction("完成至少 3 套专项练习题，并将错题加入巩固练习本");
        practice.setCourseId(item.getCourseId());
        practice.setCourseName(course);
        practice.setCurrentScore(item.getStudentAvg());
        practice.setGap(gap.negate());
        practice.setPriority(2);
        list.add(practice);

        ResourceRecommendVO coach = new ResourceRecommendVO();
        coach.setResourceType("COACH");
        coach.setResourceTypeText("教师辅导");
        coach.setTitle("「" + course + "」答疑与辅导预约");
        coach.setReason("差距超过阈值，建议与任课教师沟通学习方法，必要时纳入帮扶计划");
        coach.setAction("预约教师答疑时间，携带错题清单说明卡点");
        coach.setCourseId(item.getCourseId());
        coach.setCourseName(course);
        coach.setCurrentScore(item.getStudentAvg());
        coach.setGap(gap.negate());
        coach.setPriority(3);
        list.add(coach);
        return list;
    }

    private ResourceRecommendVO buildGapRecommend(GoalVO gv) {
        ResourceRecommendVO r = new ResourceRecommendVO();
        r.setResourceType("COURSE");
        r.setResourceTypeText("目标冲刺");
        r.setTitle("达成" + gv.getGoalTypeText() + "目标：" + gv.getTargetValue());
        r.setReason(String.format("当前 %s，距目标还差 %s", gv.getCurrentValue(), gv.getGap()));
        r.setAction("将大目标拆解为周计划：每周复盘一次成绩/进度，偏差超过 5%% 及时调整学习方法");
        r.setCourseId(gv.getCourseId());
        r.setCourseName(gv.getCourseName());
        r.setCurrentScore(gv.getCurrentValue());
        r.setGap(gv.getGap());
        r.setPriority(4);
        return r;
    }

    private String goalTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "AVG_SCORE" -> "平均分目标";
            case "GPA" -> "绩点目标";
            case "COURSE" -> "单科成绩目标";
            default -> type;
        };
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "ACTIVE" -> "进行中";
            case "ACHIEVED" -> "已达成";
            case "ABANDONED" -> "已放弃";
            default -> status;
        };
    }
}
