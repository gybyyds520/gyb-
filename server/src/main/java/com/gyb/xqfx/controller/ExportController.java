package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 数据导出：提供带文档化 Schema 的原始数据，支持外部建模与分析迁移
 */
@Tag(name = "数据导出")
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Operation(summary = "导出成绩数据")
    @GetMapping("/scores")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "数据导出", operation = "导出成绩数据")
    public void scores(HttpServletResponse response, @RequestParam(required = false) Long termId)
            throws IOException {
        exportService.exportScores(response, termId);
    }

    @Operation(summary = "导出预警数据")
    @GetMapping("/warnings")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "数据导出", operation = "导出预警数据")
    public void warnings(HttpServletResponse response, @RequestParam(required = false) Long termId)
            throws IOException {
        exportService.exportWarnings(response, termId);
    }

    @Operation(summary = "导出干预工单")
    @GetMapping("/tickets")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "数据导出", operation = "导出干预工单")
    public void tickets(HttpServletResponse response) throws IOException {
        exportService.exportTickets(response);
    }

    @Operation(summary = "导出学业指标")
    @GetMapping("/indicators")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    @Audit(module = "数据导出", operation = "导出学业指标")
    public void indicators(HttpServletResponse response, @RequestParam(required = false) Long termId)
            throws IOException {
        exportService.exportIndicators(response, termId);
    }

    @Operation(summary = "查询导出字段 Schema")
    @GetMapping("/schema")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<Map<String, String>>> schema(@RequestParam String type) {
        return Result.success(exportService.schema(type));
    }
}
