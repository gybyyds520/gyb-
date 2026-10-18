package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.InterventionLog;
import com.gyb.xqfx.entity.InterventionTicket;
import com.gyb.xqfx.model.vo.InterventionTicketVO;
import com.gyb.xqfx.model.vo.TicketSlaVO;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.AiAnalysisService;
import com.gyb.xqfx.service.InterventionService;
import com.gyb.xqfx.service.TicketSlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 干预闭环（预警 → 工单 → 帮扶 → 评估）
 */
@Tag(name = "干预闭环")
@RestController
@RequestMapping("/intervention")
@RequiredArgsConstructor
public class InterventionController {

    private final InterventionService interventionService;
    private final TicketSlaService ticketSlaService;
    private final AiAnalysisService aiAnalysisService;

    @Operation(summary = "AI 生成干预帮扶方案（教师确认后可用于创建工单）")
    @PostMapping("/ai-plan/{warningId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AiAnalysisService.AiResult> aiPlan(@PathVariable Long warningId) {
        return Result.success(
                aiAnalysisService.generateInterventionPlan(warningId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "干预工单列表")
    @GetMapping("/tickets")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<InterventionTicketVO>> tickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long studentId) {
        return Result.success(interventionService.listTickets(status, studentId));
    }

    @Operation(summary = "由预警创建干预工单")
    @PostMapping("/tickets")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<InterventionTicket> create(
            @RequestParam Long warningId,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "TALK") String interveneType,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {
        return Result.success(
                interventionService.createFromWarning(warningId, ownerUserId, interveneType, plan, dueDate),
                "工单创建成功");
    }

    @Operation(summary = "关闭工单并评估效果")
    @PostMapping("/tickets/{id}/close")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "干预闭环", operation = "关闭工单并评估效果")
    public Result<Boolean> close(@PathVariable Long id,
                                 @RequestParam(required = false) String conclusion,
                                 @RequestParam(defaultValue = "SLIGHT") String effectEval) {
        return Result.success(interventionService.closeTicket(id, conclusion, effectEval), "工单已关闭");
    }

    @Operation(summary = "追加干预记录")
    @PostMapping("/tickets/{id}/logs")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<InterventionLog> appendLog(@PathVariable Long id,
                                             @RequestParam(defaultValue = "ACTION") String actionType,
                                             @RequestParam String content,
                                             @RequestParam(required = false) Long operatorId) {
        return Result.success(interventionService.appendLog(id, actionType, content, operatorId));
    }

    @Operation(summary = "查询干预过程记录")
    @GetMapping("/tickets/{id}/logs")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<InterventionLog>> logs(@PathVariable Long id) {
        return Result.success(interventionService.listLogs(id));
    }

    @Operation(summary = "扫描超时工单并执行升级与催办")
    @PostMapping("/sla/scan")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "干预闭环", operation = "扫描工单超时并升级")
    public Result<Integer> scanOverdue() {
        int count = ticketSlaService.scanOverdue();
        return Result.success(count, "扫描完成，超时 " + count + " 条");
    }

    @Operation(summary = "处置时效统计（及时率/结案率/平均时长/责任人排行）")
    @GetMapping("/sla/statistics")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<TicketSlaVO> slaStatistics() {
        return Result.success(ticketSlaService.statistics());
    }

    @Operation(summary = "辅导员对预警有效性打标")
    @PostMapping("/tickets/{id}/feedback")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Boolean> feedback(@PathVariable Long id,
                                    @RequestParam String feedback,
                                    @RequestParam(required = false) String note) {
        return Result.success(ticketSlaService.feedback(id, feedback, note), "反馈已记录");
    }
}
