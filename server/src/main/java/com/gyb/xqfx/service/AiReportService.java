package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.AiCallLog;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.AiCallLogMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 家长端 AI 学情解读（会员功能）
 *
 * <p>调用本地大模型对子女的学业数据做通俗解读并给出家庭陪伴建议；
 * 模型不可用或输出异常时降级为规则化解读，保证家长始终能看到内容。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private static final String SYSTEM_PROMPT =
            "你是一位善于与家长沟通的学业指导老师。请用通俗易懂、温和而具体的语言，"
                    + "基于给定的学业数据给出分析与建议，不要杜撰数据，不要使用表格，控制在 400 字以内。";

    private final StudentParentMapper studentParentMapper;
    private final StudentMapper studentMapper;
    private final ScoreMapper scoreMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AcademicIndicatorService indicatorService;
    private final OllamaClient ollamaClient;

    /**
     * 生成子女的 AI 学情解读
     */
    public AiReport report(Long studentId, Long termId) {
        requireChild(studentId);
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(termId != null, Score::getTermId, termId)
                .isNotNull(Score::getTotalScore));
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>().eq(WarningRecord::getStudentId, studentId));
        AcademicIndicatorVO indicator = indicatorService.calculate(studentId, termId);

        AiReport vo = new AiReport();
        vo.setStudentId(studentId);
        vo.setStudentName(student.getName());
        vo.setScoreCount(scores.size());
        vo.setWarningCount(warnings.size());

        if (scores.isEmpty()) {
            vo.setSource("RULE");
            vo.setContent("暂无成绩数据，无法生成学情解读。请在有成绩记录后再试。");
            return vo;
        }

        double avg = scores.stream().map(Score::getTotalScore)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        long pass = scores.stream().filter(s -> Integer.valueOf(1).equals(s.getIsPass())).count();
        vo.setAvgScore(BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));

        String prompt = buildPrompt(student, scores, warnings, indicator, avg, pass);
        long start = System.currentTimeMillis();
        OllamaClient.ChatResult chat = ollamaClient.chat(SYSTEM_PROMPT, prompt);
        int costMs = (int) (System.currentTimeMillis() - start);

        if (chat != null && chat.content != null && !chat.content.isBlank()) {
            vo.setSource("AI");
            vo.setModel(chat.model);
            vo.setCostMs(costMs);
            vo.setContent(chat.content.trim());
            logCall(studentId, chat.model, chat.promptTokens, chat.completionTokens, costMs, "SUCCESS", null);
        } else {
            vo.setSource("RULE");
            vo.setCostMs(costMs);
            vo.setContent(fallbackText(student, scores.size(), avg, pass, warnings.size(), indicator));
            logCall(studentId, null, 0, 0, costMs, "FALLBACK", "本地大模型不可用，已降级");
        }
        return vo;
    }

    /** 校验子女属于当前家长 */
    private void requireChild(Long studentId) {
        Long userId = SecurityUtils.currentUserId();
        List<Long> childIds = studentParentMapper.selectList(new LambdaQueryWrapper<StudentParent>()
                        .eq(StudentParent::getParentUserId, userId)
                        .eq(StudentParent::getStatus, 1))
                .stream().map(StudentParent::getStudentId).toList();
        if (studentId == null || !childIds.contains(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该学生不在您的监护范围内");
        }
    }

    private String buildPrompt(Student student, List<Score> scores, List<WarningRecord> warnings,
                               AcademicIndicatorVO indicator, double avg, long pass) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生姓名：").append(student.getName()).append("\n");
        sb.append("成绩门数：").append(scores.size())
                .append("，平均分：").append(String.format("%.2f", avg))
                .append("，及格门数：").append(pass).append("\n");
        if (indicator != null) {
            sb.append("学业指标：GPA ").append(indicator.getGpa() == null ? "-" : indicator.getGpa())
                    .append("，修读课程 ").append(indicator.getCourseCount() == null ? "-" : indicator.getCourseCount())
                    .append(" 门，不及格 ").append(indicator.getFailCount() == null ? "-" : indicator.getFailCount())
                    .append(" 门，风险判定 ").append(indicator.getRiskFlag() == null ? "-" : indicator.getRiskFlag())
                    .append("\n");
        }
        sb.append("当前预警条数：").append(warnings.size()).append("\n");
        sb.append("各课程成绩：\n");
        scores.stream().limit(20).forEach(s ->
                sb.append("  - 课程ID ").append(s.getCourseId())
                        .append(" 总评 ").append(s.getTotalScore())
                        .append(" 是否及格 ").append(Integer.valueOf(1).equals(s.getIsPass()) ? "是" : "否")
                        .append("\n"));
        sb.append("\n请从以下三方面给出解读：1）整体学业状态；2）可能的原因或风险；3）家长可以怎么配合（具体可操作）。");
        return sb.toString();
    }

    private String fallbackText(Student student, int count, double avg, long pass,
                                int warningCount, AcademicIndicatorVO indicator) {
        StringBuilder sb = new StringBuilder();
        sb.append(student.getName()).append("同学本学期共 ").append(count)
                .append(" 门课程有成绩记录，平均 ").append(String.format("%.2f", avg))
                .append(" 分，及格 ").append(pass).append(" 门");
        if (indicator != null && indicator.getGpa() != null) {
            sb.append("，GPA 约 ").append(indicator.getGpa());
        }
        if (indicator != null && indicator.getFailCount() != null && indicator.getFailCount() > 0) {
            sb.append("，其中 ").append(indicator.getFailCount()).append(" 门不及格");
        }
        sb.append("。\n");
        if (avg >= 85) {
            sb.append("整体学业表现良好，建议保持现有节奏，可适当拓展拔高。\n");
        } else if (avg >= 60) {
            sb.append("整体处于及格区间，建议关注相对薄弱的课程，提前复习与查漏补缺。\n");
        } else {
            sb.append("整体学业压力较大，建议尽快与辅导员或任课教师沟通，制定补弱计划。\n");
        }
        if (warningCount > 0) {
            sb.append("当前有 ").append(warningCount).append(" 条学业预警记录，请重点关注并及时与学校沟通。\n");
        }
        sb.append("（说明：本地大模型暂不可用，以上为系统规则生成的解读。）");
        return sb.toString();
    }

    private void logCall(Long bizId, String model, int promptTokens, int completionTokens,
                         int costMs, String status, String errorMsg) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setBizType("VIP_AI_REPORT");
            callLog.setBizId(bizId);
            callLog.setModelName(model);
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setTotalTokens(promptTokens + completionTokens);
            callLog.setCostMs(costMs);
            callLog.setStatus(status);
            callLog.setErrorMsg(errorMsg);
            callLog.setUserId(SecurityUtils.currentUserIdOrNull());
            callLog.setCreateTime(LocalDateTime.now());
            aiCallLogMapper.insert(callLog);
        } catch (Exception e) {
            log.warn("写入大模型调用日志失败：{}", e.getMessage());
        }
    }

    /** AI 学情解读 */
    @Data
    public static class AiReport {
        private Long studentId;
        private String studentName;
        private Integer scoreCount;
        private Integer warningCount;
        private BigDecimal avgScore;
        /** AI 模型生成 / RULE 规则降级 */
        private String source;
        private String model;
        private Integer costMs;
        private String content;
    }
}
