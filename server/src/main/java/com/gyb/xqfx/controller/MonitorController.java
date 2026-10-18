package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.PageResult;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.model.vo.MonitorVO;
import com.gyb.xqfx.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员监察中心：监察三端（学生端/教师端/家长端）内容与数据。
 * 管理员拥有至高无上权限：可查看任意端全部记录与任意账号数据全景。
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

    @Operation(summary = "任意维度全量记录（至高无上权限）")
    @RequireRole("ADMIN")
    @GetMapping("/records")
    public Result<PageResult<MonitorVO.RecordRow>> records(
            @RequestParam(defaultValue = "warning") String dimension,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(monitorService.records(dimension, current, size));
    }

    @Operation(summary = "三端全部账号")
    @RequireRole("ADMIN")
    @GetMapping("/accounts")
    public Result<List<MonitorVO.Account>> accounts(@RequestParam(defaultValue = "STUDENT") String role) {
        return Result.success(monitorService.accounts(role));
    }

    @Operation(summary = "任意账号数据全景")
    @RequireRole("ADMIN")
    @GetMapping("/account/{id}")
    public Result<MonitorVO.AccountDetail> accountDetail(@PathVariable Long id) {
        return Result.success(monitorService.accountDetail(id));
    }
}
