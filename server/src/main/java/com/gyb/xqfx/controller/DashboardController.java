package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import com.gyb.xqfx.model.vo.DashboardVO;
import com.gyb.xqfx.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 系统概览仪表盘
 */
@Tag(name = "系统概览")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final int LATEST_WARNING_SIZE = 8;

    private final DashboardService dashboardService;
    private final WarningRecordMapper warningRecordMapper;
    private final com.gyb.xqfx.service.DataScopeService dataScopeService;

    @Operation(summary = "系统概览统计")
    @GetMapping("/overview")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<DashboardVO> overview(@RequestParam(required = false) Long termId) {
        return Result.success(dashboardService.overview(termId));
    }

    @Operation(summary = "班级学情对比")
    @GetMapping("/clazz-comparison")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<DashboardVO.ClazzStat>> clazzComparison(@RequestParam(required = false) Long termId) {
        return Result.success(dashboardService.clazzComparison(termId));
    }

    @Operation(summary = "最新待处理预警")
    @GetMapping("/latest-warnings")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<WarningRecord>> latestWarnings() {
        // 数据范围：仅返回可见学生的待处理预警（含学生姓名/联系方式，必须按范围过滤）
        java.util.List<Long> scope = dataScopeService.scopeForQuery();
        List<WarningRecord> list = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getStatus, "ACTIVE")
                        .in(scope != null && !scope.isEmpty(), WarningRecord::getStudentId, scope)
                        .orderByDesc(WarningRecord::getWarningLevel)
                        .last("LIMIT " + LATEST_WARNING_SIZE));
        return Result.success(list);
    }
}
