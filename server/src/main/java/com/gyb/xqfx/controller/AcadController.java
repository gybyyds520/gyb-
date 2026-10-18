package com.gyb.xqfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.ImportTask;
import com.gyb.xqfx.mapper.ImportTaskMapper;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.acad.AcadSyncResult;
import com.gyb.xqfx.service.acad.AcadSystemAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 教务系统对接（适配器模式：预留接口 + Mock 实现）
 */
@Tag(name = "教务对接")
@RestController
@RequestMapping("/acad")
@RequiredArgsConstructor
@Slf4j
public class AcadController {

    private final AcadSystemAdapter acadSystemAdapter;
    private final ImportTaskMapper importTaskMapper;

    @Value("${xqfx.acad.provider:mock}")
    private String provider;

    private static final String ACAD_PREFIX = "ACAD_";

    @Operation(summary = "教务适配器状态")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN"})
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "provider", acadSystemAdapter.providerName(),
                "configured", provider,
                "available", true,
                "desc", "教务系统对接适配器（预留接口 + Mock 实现），切换配置 xqfx.acad.provider 即可接入真实教务系统"));
    }

    @Operation(summary = "执行教务同步")
    @Audit(module = "教务对接", operation = "执行教务数据同步")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN"})
    @PostMapping("/sync")
    public Result<AcadSyncResult> sync(@RequestParam String type) {
        AcadSyncResult res;
        String itemType;
        switch (type.toUpperCase()) {
            case "STUDENTS" -> {
                res = acadSystemAdapter.syncStudents();
                itemType = "STUDENTS";
            }
            case "COURSES" -> {
                res = acadSystemAdapter.syncCourses();
                itemType = "COURSES";
            }
            case "SCORES" -> {
                res = acadSystemAdapter.syncScores();
                itemType = "SCORES";
            }
            case "ATTENDANCE" -> {
                res = acadSystemAdapter.syncAttendance();
                itemType = "ATTENDANCE";
            }
            default -> {
                return Result.fail("不支持的同步类型：" + type + "（STUDENTS/COURSES/SCORES/ATTENDANCE）");
            }
        }
        recordSyncTask(itemType, res);
        return Result.success(res, res.getMessage());
    }

    @Operation(summary = "教务同步记录")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN"})
    @GetMapping("/sync/logs")
    public Result<List<ImportTask>> logs() {
        List<ImportTask> list = importTaskMapper.selectList(
                new LambdaQueryWrapper<ImportTask>()
                        .likeRight(ImportTask::getImportType, ACAD_PREFIX)
                        .orderByDesc(ImportTask::getId)
                        .last("limit 50"));
        return Result.success(list);
    }

    private void recordSyncTask(String itemType, AcadSyncResult res) {
        ImportTask task = new ImportTask();
        task.setTaskNo("ACAD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        task.setImportType(ACAD_PREFIX + itemType);
        task.setFileName("教务系统同步-" + itemType);
        task.setTotalCount(res.getTotalCount());
        task.setSuccessCount(res.getSuccessCount());
        task.setFailCount(res.getFailCount() == null ? 0 : res.getFailCount());
        task.setStatus("SUCCESS");
        task.setOperatorId(SecurityUtils.currentUserIdOrNull());
        task.setStartTime(LocalDateTime.now());
        task.setEndTime(LocalDateTime.now());
        try {
            importTaskMapper.insert(task);
        } catch (Exception e) {
            log.warn("教务同步记录写入失败：{}", e.getMessage());
        }
    }
}
