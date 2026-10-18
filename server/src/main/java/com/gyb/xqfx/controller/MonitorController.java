package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.model.vo.MonitorVO;
import com.gyb.xqfx.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员监察中心：监察三端（学生端/教师端/家长端）内容与数据
 */
@Tag(name = "监察中心")
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @Operation(summary = "三端 + 系统数据概览")
    @RequireRole("ADMIN")
    @GetMapping("/overview")
    public Result<MonitorVO.Overview> overview() {
        return Result.success(monitorService.overview());
    }

    @Operation(summary = "各端最新动态")
    @RequireRole("ADMIN")
    @GetMapping("/latest")
    public Result<MonitorVO.Latest> latest() {
        return Result.success(monitorService.latest());
    }

    @Operation(summary = "指定学生数据全景")
    @RequireRole("ADMIN")
    @GetMapping("/student/{studentId}")
    public Result<MonitorVO.StudentPanorama> student(@PathVariable Long studentId) {
        return Result.success(monitorService.student(studentId));
    }
}
