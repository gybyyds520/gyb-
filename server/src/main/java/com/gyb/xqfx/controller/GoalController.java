package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.Audit;
import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.LearningGoal;
import com.gyb.xqfx.model.vo.GoalVO;
import com.gyb.xqfx.model.vo.ResourceRecommendVO;
import com.gyb.xqfx.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习目标与资源推荐
 */
@Tag(name = "学习目标")
@RestController
@RequestMapping("/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "学习目标列表（含当前值与差距；学生本人无需传 studentId）")
    @GetMapping("/list")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<List<GoalVO>> list(@RequestParam(required = false) Long studentId,
                                     @RequestParam(required = false) Long termId) {
        return Result.success(goalService.list(studentId, termId));
    }

    @Operation(summary = "保存学习目标（新建或更新）")
    @PostMapping("/save")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    @Audit(module = "学习目标", operation = "保存目标")
    public Result<Long> save(@RequestParam(required = false) Long studentId,
                             @RequestBody LearningGoal goal) {
        return Result.success(goalService.save(studentId, goal), "目标已保存");
    }

    @Operation(summary = "更新目标状态（ACHIEVED/ABANDONED/ACTIVE）")
    @PostMapping("/{id}/status")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<Boolean> updateStatus(@PathVariable Long id,
                                        @RequestParam String status) {
        return Result.success(goalService.updateStatus(id, status), "状态已更新");
    }

    @Operation(summary = "删除学习目标")
    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(goalService.delete(id), "目标已删除");
    }

    @Operation(summary = "学习资源推荐（基于偏科弱科与目标差距）")
    @GetMapping("/recommendations")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT"})
    public Result<List<ResourceRecommendVO>> recommendations(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long termId) {
        return Result.success(goalService.recommendations(studentId, termId));
    }
}
