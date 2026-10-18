package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.InterventionTicketMapper;
import com.gyb.xqfx.mapper.PredictionResultMapper;
import com.gyb.xqfx.mapper.ReportRecordMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统概览仪表盘服务：聚合各业务模块的核心指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 预警等级名称：1蓝 2黄 3橙 4红 */
    private static final String[] LEVEL_NAMES = {"蓝色", "黄色", "橙色", "红色"};

    /** 成绩分数段名称 */
    private static final String[] SEGMENT_NAMES = {"60分以下", "60-69", "70-79", "80-89", "90分及以上"};

    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final ClazzMapper clazzMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final InterventionTicketMapper ticketMapper;

    /**
     * 获取系统概览
     *
     * @param termId 学期ID，为空表示全部学期
     */
    public DashboardVO overview(Long termId) {
        DashboardVO vo = new DashboardVO();

        // 仅统计在校学生，毕业/休学等状态不计入
        vo.setStudentCount(studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getStatus, "ENROLLED")));
        vo.setClazzCount(clazzMapper.selectCount(null));
        vo.setScoreCount(scoreMapper.selectCount(null));
        vo.setPredictionCount(predictionResultMapper.selectCount(null));
        vo.setActiveWarningCount(warningRecordMapper.selectCount(
                new LambdaQueryWrapper<com.gyb.xqfx.entity.WarningRecord>()
                        .eq(com.gyb.xqfx.entity.WarningRecord::getStatus, "ACTIVE")));
        vo.setReportCount(reportRecordMapper.selectCount(null));
        vo.setOpenTicketCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<com.gyb.xqfx.entity.InterventionTicket>()
                        .ne(com.gyb.xqfx.entity.InterventionTicket::getStatus, "CLOSED")));

        // 总体平均成绩与及格率：由各学期统计按记录数加权
        List<DashboardVO.TermStat> terms = safeTermStats();
        vo.setTermTrend(terms);
        BigDecimal totalScoreSum = BigDecimal.ZERO;
        long totalCount = 0;
        BigDecimal passWeighted = BigDecimal.ZERO;
        for (DashboardVO.TermStat t : terms) {
            if (t.getAvgScore() == null || t.getScoreCount() == null) {
                continue;
            }
            totalScoreSum = totalScoreSum.add(t.getAvgScore().multiply(BigDecimal.valueOf(t.getScoreCount())));
            totalCount += t.getScoreCount();
            if (t.getPassRate() != null) {
                passWeighted = passWeighted.add(t.getPassRate().multiply(BigDecimal.valueOf(t.getScoreCount())));
            }
        }
        if (totalCount > 0) {
            vo.setAvgScore(totalScoreSum.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP));
            vo.setPassRate(passWeighted.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP));
        } else {
            vo.setAvgScore(BigDecimal.ZERO);
            vo.setPassRate(BigDecimal.ZERO);
        }

        vo.setScoreDistribution(scoreDistribution(termId));
        vo.setWarningDistribution(warningDistribution());
        vo.setClazzRanking(clazzRanking(termId));
        return vo;
    }

    /**
     * 成绩分数段分布
     */
    public List<DashboardVO.NameValue> scoreDistribution(Long termId) {
        Map<String, Object> seg = new HashMap<>();
        try {
            Map<String, Object> result = scoreMapper.statSegments(termId);
            if (result != null) {
                seg = result;
            }
        } catch (Exception e) {
            log.warn("统计分数段分布失败：{}", e.getMessage());
        }
        List<DashboardVO.NameValue> list = new ArrayList<>();
        String[] keys = {"seg1", "seg2", "seg3", "seg4", "seg5"};
        for (int i = 0; i < keys.length; i++) {
            Object v = seg.get(keys[i]);
            long count = v == null ? 0L : ((Number) v).longValue();
            list.add(new DashboardVO.NameValue(SEGMENT_NAMES[i], count));
        }
        return list;
    }

    /**
     * 预警等级分布（1-4 级，缺失等级补 0）
     */
    public List<DashboardVO.NameValue> warningDistribution() {
        Map<Integer, Long> counts = new HashMap<>();
        try {
            for (Map<String, Object> row : warningRecordMapper.statByLevel()) {
                Object level = row.get("level");
                Object cnt = row.get("cnt");
                if (level == null) {
                    continue;
                }
                counts.put(((Number) level).intValue(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        } catch (Exception e) {
            log.warn("统计预警等级分布失败：{}", e.getMessage());
        }
        List<DashboardVO.NameValue> list = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            list.add(new DashboardVO.NameValue(LEVEL_NAMES[i - 1], counts.getOrDefault(i, 0L)));
        }
        return list;
    }

    /**
     * 班级成绩排名，并附带各班预警人数
     */
    public List<DashboardVO.ClazzStat> clazzRanking(Long termId) {
        List<DashboardVO.ClazzStat> stats = safeClazzStats(termId);
        Map<Long, Long> warningCounts = new HashMap<>();
        try {
            for (Map<String, Object> row : warningRecordMapper.countByClazz()) {
                Object clazzId = row.get("clazzId");
                Object cnt = row.get("cnt");
                if (clazzId == null) {
                    continue;
                }
                warningCounts.put(((Number) clazzId).longValue(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        } catch (Exception e) {
            log.warn("统计班级预警人数失败：{}", e.getMessage());
        }
        stats.forEach(s -> s.setWarningCount(warningCounts.getOrDefault(s.getClazzId(), 0L)));
        return stats;
    }

    /**
     * 班级学情对比：在排名基础上补充排名序号与风险标记
     */
    public List<DashboardVO.ClazzStat> clazzComparison(Long termId) {
        List<DashboardVO.ClazzStat> ranking = clazzRanking(termId);
        for (int i = 0; i < ranking.size(); i++) {
            DashboardVO.ClazzStat s = ranking.get(i);
            if (s.getAvgScore() != null) {
                s.setAvgScore(s.getAvgScore().setScale(2, RoundingMode.HALF_UP));
            }
            if (s.getPassRate() != null) {
                s.setPassRate(s.getPassRate().setScale(2, RoundingMode.HALF_UP));
            }
            if (s.getExcellentRate() != null) {
                s.setExcellentRate(s.getExcellentRate().setScale(2, RoundingMode.HALF_UP));
            }
        }
        return ranking;
    }

    private List<DashboardVO.TermStat> safeTermStats() {
        try {
            List<DashboardVO.TermStat> terms = scoreMapper.statByTerm();
            return terms == null ? List.of() : terms;
        } catch (Exception e) {
            log.warn("统计学期趋势失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<DashboardVO.ClazzStat> safeClazzStats(Long termId) {
        try {
            List<DashboardVO.ClazzStat> stats = scoreMapper.statByClazz(termId);
            return stats == null ? new ArrayList<>() : new ArrayList<>(stats);
        } catch (Exception e) {
            log.warn("统计班级成绩失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
