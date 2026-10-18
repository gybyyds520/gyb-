package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ClassPerformance;
import com.gyb.xqfx.model.vo.PerformanceVO;
import com.gyb.xqfx.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 课堂表现
 */
@Tag(name = "课堂表现")
@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @Operation(summary = "记录课堂表现")
    @PostMapping
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<ClassPerformance> record(@RequestBody ClassPerformance record) {
        return Result.success(performanceService.record(record), "已记录");
    }

    @Operation(summary = "学生课堂表现记录")
    @GetMapping("/student/{studentId}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<ClassPerformance>> listByStudent(@PathVariable Long studentId,
                                                        @RequestParam(required = false) Long termId) {
        return Result.success(performanceService.listByStudent(studentId, termId));
    }

    @Operation(summary = "学生课堂表现汇总")
    @GetMapping("/summary")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<PerformanceVO> summary(@RequestParam Long studentId,
                                         @RequestParam(required = false) Long termId) {
        return Result.success(performanceService.summary(studentId, termId));
    }

    @Operation(summary = "课堂表现排名")
    @GetMapping("/ranking")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<PerformanceVO>> ranking(@RequestParam(required = false) Long termId,
                                               @RequestParam(defaultValue = "20") int limit) {
        return Result.success(performanceService.ranking(termId, limit));
    }

    @Operation(summary = "生成演示课堂表现数据")
    @PostMapping("/demo")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> demo(@RequestParam(required = false) Long termId,
                                            @RequestParam(defaultValue = "3") int timesPerStudent) {
        int count = performanceService.generateDemo(termId, timesPerStudent);
        return Result.success(Map.of("count", count), "已生成 " + count + " 条课堂表现记录");
    }
}
