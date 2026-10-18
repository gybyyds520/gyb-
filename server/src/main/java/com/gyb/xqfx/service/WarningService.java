package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.entity.PredictionResult;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.entity.WarningRule;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.mapper.WarningRuleMapper;
import com.gyb.xqfx.model.vo.WarningAnalyticsVO;
import com.gyb.xqfx.model.vo.WarningVO;
import com.gyb.xqfx.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学业预警引擎：规则匹配 → 等级判定 → 去重抑制 → 预警落库
 *
 * <p>等级：1蓝（低）2黄（中）3橙（高）4红（极高）</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningService {

    /** 规则阈值采用 0-100 分制，模型概率为 0-1，比较前统一换算 */
    private static final BigDecimal SCALE = new BigDecimal("100");

    /** 概率低于所有规则阈值但仍需关注的下限 */
    private static final BigDecimal MIN_ATTENTION_PROBABILITY = new BigDecimal("0.15");

    private final WarningRuleMapper ruleMapper;
    private final WarningRecordMapper recordMapper;
    private final com.gyb.xqfx.mapper.StudentMapper studentMapper;
    private final com.gyb.xqfx.mapper.ClazzMapper clazzMapper;
    private final ObjectMapper objectMapper;
    private final WarningAnalyticsService warningAnalyticsService;

    /**
     * 加载启用中的规则，按等级从高到低排序
     */
    public List<WarningRule> enabledRules() {
        // 拷贝为可变列表后再排序，避免不可变结果集导致排序失败
        List<WarningRule> rules = new ArrayList<>(ruleMapper.selectList(
                new LambdaQueryWrapper<WarningRule>().eq(WarningRule::getEnabled, 1)));
        rules.sort(Comparator.comparing(WarningRule::getLevel).reversed());
        return rules;
    }

    /**
     * 按风险概率匹配预警规则：命中等级最高的规则
     *
     * @return 命中的规则；无需预警时返回 null
     */
    public WarningRule matchRule(BigDecimal probability) {
        if (probability == null) {
            return null;
        }
        List<WarningRule> rules = enabledRules();
        if (rules.isEmpty()) {
            return null;
        }
        // 概率换算为 0-100 风险分后与规则阈值比较
        BigDecimal score = probability.multiply(SCALE);
        // 规则按等级降序，命中即返回最高等级
        for (WarningRule rule : rules) {
            if (rule.getScoreThreshold() != null && score.compareTo(rule.getScoreThreshold()) >= 0) {
                return rule;
            }
        }
        // 未命中任何阈值：概率仍偏高时以最低等级规则提示关注
        if (probability.compareTo(MIN_ATTENTION_PROBABILITY) >= 0) {
            return rules.stream()
                    .filter(r -> r.getScoreThreshold() != null)
                    .min(Comparator.comparing(WarningRule::getLevel))
                    .orElse(null);
        }
        return null;
    }

    /**
     * 依据预测结果批量生成预警（含去重抑制）
     *
     * @return 新增预警条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateFromPredictions(Long termId, List<PredictionResult> results) {
        List<WarningRecord> created = new ArrayList<>();
        for (PredictionResult pr : results) {
            WarningRule rule = matchRule(pr.getRiskProbability());
            String detail;
            int level;
            Long ruleId = null;
            String title;
            String content;

            if (rule != null) {
                level = rule.getLevel() == null ? pr.getRiskLevel() : rule.getLevel();
                ruleId = rule.getId();
                detail = buildTriggerDetail(rule, pr);
                title = rule.getRuleName();
                content = rule.getDescription() != null ? rule.getDescription() : rule.getRuleName();
            } else {
                // 综合概率未达阈值，但学业指标出现单维度极端异常时仍应预警，
                // 避免加权汇总把某一维度的严重问题稀释掉
                WarningAnalyticsVO.AcademicTrigger trigger =
                        warningAnalyticsService.evalAcademicTrigger(pr.getStudentId(), termId);
                if (trigger == null || !Boolean.TRUE.equals(trigger.getTriggered())) {
                    continue;
                }
                level = trigger.getSuggestLevel();
                detail = "学业指标触发：" + trigger.getReason();
                title = "学业指标异常预警";
                content = trigger.getReason();
            }

            if (suppressed(pr.getStudentId(), termId, level)) {
                continue;
            }
            WarningRecord record = new WarningRecord();
            record.setWarningNo(generateWarningNo(pr.getStudentId(), level));
            record.setStudentId(pr.getStudentId());
            record.setTermId(termId);
            record.setRuleId(ruleId);
            record.setPredictionId(pr.getId());
            record.setWarningLevel(level);
            // 规则命中时沿用规则维度类型（学业/出勤/作业/挂科/进度/心理/行为/消费等），
            // 学业指标单维触发仍归为 ACADEMIC，保证预警可按维度治理
            record.setWarningType(rule != null && rule.getWarningType() != null
                    ? rule.getWarningType() : "ACADEMIC");
            record.setRiskProbability(pr.getRiskProbability());
            record.setTriggerDetail(detail);
            record.setStatus("ACTIVE");
            record.setTitle(title);
            record.setContent(content);
            record.setTriggerValue(pr.getRiskProbability().multiply(SCALE));
            created.add(record);
        }

        for (WarningRecord r : created) {
            recordMapper.insert(r);
        }
        log.info("预警生成完成：学期 {} 新增 {} 条", termId, created.size());
        return created.size();
    }

    /**
     * 去重抑制：同一学生同一学期已存在同等级或更高等级的未关闭预警时跳过
     */
    public boolean suppressed(Long studentId, Long termId, int level) {
        List<WarningRecord> existing = recordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getStudentId, studentId)
                        .eq(WarningRecord::getTermId, termId)
                        .eq(WarningRecord::getStatus, "ACTIVE"));
        return existing.stream().anyMatch(w -> w.getWarningLevel() != null && w.getWarningLevel() >= level);
    }

    /**
     * 分页查询预警（带学生姓名与班级）
     */
    public IPage<WarningVO> pageWarnings(long current, long size, Integer level, String status,
                                         Long termId, String warningType, boolean masked) {
        LambdaQueryWrapper<WarningRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(level != null, WarningRecord::getWarningLevel, level);
        wrapper.eq(status != null && !status.isEmpty(), WarningRecord::getStatus, status);
        wrapper.eq(termId != null, WarningRecord::getTermId, termId);
        wrapper.eq(warningType != null && !warningType.isEmpty(), WarningRecord::getWarningType, warningType);
        wrapper.orderByDesc(WarningRecord::getWarningLevel).orderByDesc(WarningRecord::getId);

        Page<WarningRecord> page = recordMapper.selectPage(new Page<>(current, size), wrapper);

        List<Long> studentIds = page.getRecords().stream()
                .map(WarningRecord::getStudentId).filter(Objects::nonNull).distinct().toList();
        Map<Long, com.gyb.xqfx.entity.Student> students = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(com.gyb.xqfx.entity.Student::getId, s -> s, (a, b) -> a));

        List<Long> clazzIds = students.values().stream()
                .map(com.gyb.xqfx.entity.Student::getClazzId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> clazzNames = clazzIds.isEmpty() ? Map.of()
                : clazzMapper.selectBatchIds(clazzIds).stream()
                .collect(Collectors.toMap(com.gyb.xqfx.entity.Clazz::getId,
                        com.gyb.xqfx.entity.Clazz::getClazzName, (a, b) -> a));

        List<WarningVO> vos = page.getRecords().stream().map(w -> {
            WarningVO vo = new WarningVO();
            org.springframework.beans.BeanUtils.copyProperties(w, vo);
            com.gyb.xqfx.entity.Student s = students.get(w.getStudentId());
            if (s != null) {
                vo.setStudentNo(s.getStudentNo());
                vo.setStudentName(masked ? MaskUtils.maskName(s.getName()) : s.getName());
                vo.setPhone(masked ? MaskUtils.maskPhone(s.getPhone()) : s.getPhone());
                vo.setClazzName(clazzNames.get(s.getClazzId()));
            }
            return vo;
        }).toList();

        Page<WarningVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 处理预警（指派责任人 + 更新状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handle(Long warningId, String status, Long ownerUserId, String handleResult) {
        WarningRecord record = recordMapper.selectById(warningId);
        if (record == null) {
            throw new BusinessException("预警记录不存在");
        }
        record.setStatus(status);
        record.setOwnerUserId(ownerUserId);
        record.setHandleResult(handleResult);
        record.setHandleTime(LocalDateTime.now());
        return recordMapper.updateById(record) > 0;
    }

    /**
     * 统计各等级预警数量
     */
    public Map<Integer, Long> statistics(Long termId) {
        List<WarningRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(termId != null, WarningRecord::getTermId, termId)
                        .eq(WarningRecord::getStatus, "ACTIVE"));
        return records.stream()
                .filter(w -> w.getWarningLevel() != null)
                .collect(Collectors.groupingBy(WarningRecord::getWarningLevel, Collectors.counting()));
    }

    /**
     * 统计各维度类型预警数量（学业/出勤/作业/挂科/进度/心理/行为等）
     */
    public Map<String, Long> typeStatistics(Long termId) {
        List<WarningRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(termId != null, WarningRecord::getTermId, termId)
                        .eq(WarningRecord::getStatus, "ACTIVE"));
        return records.stream()
                .filter(w -> w.getWarningType() != null && !w.getWarningType().isBlank())
                .collect(Collectors.groupingBy(WarningRecord::getWarningType, Collectors.counting()));
    }

    private String generateWarningNo(Long studentId, int level) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "W" + date + "-" + level + "-" + studentId;
    }

    private String buildTriggerDetail(WarningRule rule, PredictionResult pr) {
        try {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("ruleCode", rule.getRuleCode());
            detail.put("ruleName", rule.getRuleName());
            detail.put("riskProbability", pr.getRiskProbability());
            detail.put("threshold", rule.getScoreThreshold());
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return rule.getRuleName();
        }
    }
}
