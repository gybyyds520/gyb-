package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.DataQualityReport;
import com.gyb.xqfx.model.vo.QualityReportVO;
import com.gyb.xqfx.service.QualityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 数据质量报告
 */
@Tag(name = "数据质量")
@RestController
@RequestMapping("/data/quality")
@RequiredArgsConstructor
public class QualityReportController {

    private final QualityReportService qualityReportService;

    @Operation(summary = "执行全量数据质量校验并生成报告")
    @PostMapping("/check")
    @RequireRole({"ADMIN"})
    public Result<Map<String, Object>> check(@RequestParam(required = false) Long taskId) {
        int count = qualityReportService.runFullCheck(taskId);
        return Result.success(Map.of("reportCount", count), "校验完成，生成 " + count + " 条报告");
    }

    @Operation(summary = "质量报告列表")
    @GetMapping("/reports")
    @RequireRole({"ADMIN"})
    public Result<List<DataQualityReport>> reports(@RequestParam(required = false) String dataset) {
        return Result.success(qualityReportService.list(dataset));
    }

    @Operation(summary = "质量汇总（问题总数、分布、健康度）")
    @GetMapping("/summary")
    @RequireRole({"ADMIN"})
    public Result<QualityReportVO> summary() {
        return Result.success(qualityReportService.summary());
    }
}
