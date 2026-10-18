package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.CourseCheckin;
import com.gyb.xqfx.security.SecurityUtils;
import com.gyb.xqfx.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课堂签到（学习通装载第一批）
 *
 * <p>教师发起数字码签到、结束签到、查看统计、补签；学生输码签到（写入考勤）。</p>
 */
@Tag(name = "课堂签到")
@RestController
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "教师发起签到（返回签到码）")
    @PostMapping("/checkin")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<CourseCheckin> create(@RequestParam("offeringId") Long offeringId,
                                        @RequestParam(value = "title", required = false) String title) {
        return Result.success(checkinService.create(offeringId, SecurityUtils.currentUserId(), title));
    }

    @Operation(summary = "学生输码签到（写入考勤）")
    @PostMapping("/checkin/{id}/sign")
    @RequireRole({"STUDENT"})
    public Result<Void> sign(@PathVariable Long id, @RequestParam("code") String code) {
        checkinService.sign(SecurityUtils.currentUserId(), id, code);
        return Result.success(null);
    }

    @Operation(summary = "教师结束签到")
    @PostMapping("/checkin/{id}/close")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Void> close(@PathVariable Long id) {
        checkinService.close(id, SecurityUtils.currentUserId());
        return Result.success(null);
    }

    @Operation(summary = "教师补签")
    @PostMapping("/checkin/{id}/manual-sign")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Void> manualSign(@PathVariable Long id, @RequestParam("studentId") Long studentId) {
        checkinService.teacherSign(id, studentId, SecurityUtils.currentUserId());
        return Result.success(null);
    }

    @Operation(summary = "教学班签到活动列表")
    @GetMapping("/checkin/list")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<List<Map<String, Object>>> list(@RequestParam("offeringId") Long offeringId) {
        return Result.success(checkinService.listByOffering(offeringId));
    }

    @Operation(summary = "签到统计（应到/已到/未到）")
    @GetMapping("/checkin/{id}/stats")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER"})
    public Result<Map<String, Object>> stats(@PathVariable Long id) {
        return Result.success(checkinService.stats(id));
    }

    @Operation(summary = "学生视角：所属课程签到记录")
    @GetMapping("/checkin/student/mine")
    @RequireRole({"STUDENT"})
    public Result<List<Map<String, Object>>> studentMine() {
        return Result.success(checkinService.studentCheckins(SecurityUtils.currentUserId()));
    }
}
