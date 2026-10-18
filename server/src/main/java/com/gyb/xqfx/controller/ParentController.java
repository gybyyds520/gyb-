package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.annotation.RequireVip;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.enums.VipFeature;
import com.gyb.xqfx.service.AiReportService;
import com.gyb.xqfx.service.ParentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 家长端：查看已绑定子女的学业概况与预警提示，并提交回执
 *
 * <p>仅 PARENT 角色可访问，每个接口都校验目标学生确属本人监护范围。</p>
 */
@Tag(name = "家长端")
@RestController
@RequestMapping("/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;
    private final AiReportService aiReportService;

    @Operation(summary = "我的子女列表与学业概况")
    @GetMapping("/children")
    @RequireRole("PARENT")
    public Result<List<ParentService.ChildBrief>> children(@RequestParam(required = false) Long termId) {
        return Result.success(parentService.myChildren(termId));
    }

    @Operation(summary = "子女学业详情")
    @GetMapping("/children/{studentId}")
    @RequireRole("PARENT")
    public Result<ParentService.ChildDetail> childDetail(@PathVariable Long studentId,
                                                         @RequestParam(required = false) Long termId) {
        return Result.success(parentService.childDetail(studentId, termId));
    }

    @Operation(summary = "家长回执确认")
    @PostMapping("/feedback")
    @RequireRole("PARENT")
    public Result<Long> feedback(@RequestParam Long studentId,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) Long bizId,
                                 @RequestParam(required = false) String ackStatus,
                                 @RequestParam(required = false) String content) {
        return Result.success(parentService.submitFeedback(studentId, bizType, bizId, ackStatus, content),
                "回执已提交");
    }

    @Operation(summary = "子女 AI 学情解读（会员功能）")
    @GetMapping("/ai-report")
    @RequireRole("PARENT")
    @RequireVip(VipFeature.AI_REPORT)
    public Result<AiReportService.AiReport> aiReport(@RequestParam Long studentId,
                                                     @RequestParam(required = false) Long termId) {
        return Result.success(aiReportService.report(studentId, termId));
    }
}
