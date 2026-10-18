package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.entity.WarningRule;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.mapper.WarningRuleMapper;
import com.gyb.xqfx.model.vo.WarningAnalyticsVO;
import com.gyb.xqfx.model.vo.WarningVO;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.AiAnalysisService;
import com.gyb.xqfx.service.WarningAnalyticsService;
import com.gyb.xqfx.service.WarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 学业预警
 */
@Tag(name = "学业预警")
@RestController
@RequestMapping("/warning")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;
    private final WarningRuleMapper warningRuleMapper;
    private final WarningAnalyticsService warningAnalyticsService;
    private final WarningRecordMapper warningRecordMapper;
    private final AiAnalysisService aiAnalysisService;
    private final com.gyb.xqfx.service.DataScopeService dataScopeService;

    @Operation(summary = "预警列表分页查询")
    @GetMapping("/page")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<PageResult<WarningVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String warningType,
            @RequestParam(defaultValue = "false") boolean masked) {
        return Result.success(PageResult.of(
                warningService.pageWarnings(current, size, level, status, termId, warningType, masked)));
    }

    @Operation(summary = "预警等级统计")
    @GetMapping("/statistics")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<Integer, Long>> statistics(@RequestParam(required = false) Long termId) {
        return Result.success(warningService.statistics(termId));
    }

    @Operation(summary = "预警维度类型统计（学业/出勤/作业/挂科/进度/心理/行为等）")
    @GetMapping("/type-statistics")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Long>> typeStatistics(@RequestParam(required = false) Long termId) {
        return Result.success(warningService.typeStatistics(termId));
    }

    @Operation(summary = "处理预警")
    @PostMapping("/{id}/handle")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "学业预警", operation = "处理预警")
    public Result<Boolean> handle(@PathVariable Long id,
                                  @RequestParam(defaultValue = "HANDLED") String status,
                                  @RequestParam(required = false) Long ownerUserId,
                                  @RequestParam(required = false) String handleResult) {
        return Result.success(warningService.handle(id, status, ownerUserId, handleResult), "处理成功");
    }

    @Operation(summary = "预警多维下钻（学院/专业/年级/性别/班级）")
    @GetMapping("/drill")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<WarningAnalyticsVO.DrillItem>> drill(
            @RequestParam(required = false) Long termId,
            @RequestParam(defaultValue = "COLLEGE") String dimension) {
        return Result.success(warningAnalyticsService.drillDown(termId, dimension));
    }

    @Operation(summary = "学生风险事件时间线")
    @GetMapping("/timeline")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<WarningAnalyticsVO.TimelineItem>> timeline(@RequestParam Long studentId) {
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(warningAnalyticsService.timeline(studentId));
    }

    @Operation(summary = "学业指标触发判定")
    @GetMapping("/academic-trigger")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<WarningAnalyticsVO.AcademicTrigger> academicTrigger(
            @RequestParam Long studentId,
            @RequestParam(required = false) Long termId) {
        return Result.success(warningAnalyticsService.evalAcademicTrigger(studentId, termId));
    }

    @Operation(summary = "预警规则列表")
    @GetMapping("/rules")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<WarningRule>> rules() {
        return Result.success(warningRuleMapper.selectList(null));
    }

    @Operation(summary = "新增或更新预警规则")
    @PostMapping("/rules")
    @RequireRole({"ADMIN"})
    public Result<Boolean> saveRule(@RequestBody WarningRule rule) {
        return Result.success(rule.getId() == null
                ? warningRuleMapper.insert(rule) > 0
                : warningRuleMapper.updateById(rule) > 0);
    }

    @Operation(summary = "删除预警规则")
    @DeleteMapping("/rules/{id}")
    @RequireRole({"ADMIN"})
    public Result<Boolean> deleteRule(@PathVariable Long id) {
        return Result.success(warningRuleMapper.deleteById(id) > 0);
    }

    @Operation(summary = "预警大模型归因（因果链解读）")
    @PostMapping("/{id}/attribution")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> attribution(@PathVariable Long id) {
        WarningRecord w = warningRecordMapper.selectById(id);
        if (w == null) {
            return Result.success(null, "预警不存在");
        }
        dataScopeService.requireStudentAccess(w.getStudentId());
        AiAnalysisService.StudentContext ctx = aiAnalysisService.loadContext(w.getStudentId(), w.getTermId());
        String contextText = "【预警事件】\n"
                + "预警编号：" + (w.getWarningNo() == null ? "-" : w.getWarningNo())
                + "\n标题：" + (w.getTitle() == null ? "-" : w.getTitle())
                + "\n内容：" + (w.getContent() == null ? "-" : w.getContent())
                + "\n类型：" + (w.getWarningType() == null ? "-" : w.getWarningType())
                + "\n等级：" + (w.getWarningLevel() == null ? "-" : w.getWarningLevel())
                + "\n触发详情：" + (w.getTriggerDetail() == null ? "-" : w.getTriggerDetail())
                + "\n风险概率：" + (w.getRiskProbability() == null ? "-" : w.getRiskProbability())
                + "\n\n【该生学情上下文】\n" + ctx.toText();
        return Result.success(aiAnalysisService.generateAttribution(
                "WARNING_ATTRIBUTION", contextText, id, SecurityUtils.currentUserId()));
    }
}
