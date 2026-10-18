package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.model.vo.AcademicIndicatorVO;
import com.gyb.xqfx.service.AcademicIndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 学业核心指标（GPA / 欠学分 / 趋势 / 偏离度）
 */
@Tag(name = "学业指标")
@RestController
@RequestMapping("/academic")
@RequiredArgsConstructor
public class AcademicIndicatorController {

    private final AcademicIndicatorService academicIndicatorService;
    private final com.gyb.xqfx.service.DataScopeService dataScopeService;

    @Operation(summary = "学生学业指标（仅可见范围内的学生）")
    @GetMapping("/indicator")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<AcademicIndicatorVO> indicator(@RequestParam Long studentId,
                                                 @RequestParam(required = false) Long termId) {
        // 学生与家长请使用 /portal/indicator、/parent/children，此处强制校验数据范围
        dataScopeService.requireStudentAccess(studentId);
        return Result.success(academicIndicatorService.calculate(studentId, termId));
    }

    @Operation(summary = "全体学生学业指标（风险优先排序）")
    @GetMapping("/batch")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<AcademicIndicatorVO>> batch(@RequestParam(required = false) Long termId,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return Result.success(academicIndicatorService.batchCalculate(termId, limit));
    }

    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    @Operation(summary = "百分制成绩换算绩点")
    @GetMapping("/grade-point")
    public Result<BigDecimal> gradePoint(@RequestParam BigDecimal score) {
        return Result.success(academicIndicatorService.gradePoint(score));
    }
}
