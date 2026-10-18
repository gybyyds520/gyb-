package com.gyb.xqfx.controller;

import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.entity.NotifyMessage;
import com.gyb.xqfx.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.gyb.xqfx.annotation.RequireRole;

/**
 * 站内通知
 */
@Tag(name = "通知中心")
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @Operation(summary = "我的通知列表")
    @GetMapping("/list")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<List<NotifyMessage>> list(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) Boolean unreadOnly) {
        return Result.success(notifyService.listByUser(resolveUserId(userId), unreadOnly));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Long> unreadCount(@RequestParam(required = false) Long userId) {
        return Result.success(notifyService.unreadCount(resolveUserId(userId)));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Boolean> markRead(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return Result.success(notifyService.markRead(id, resolveUserId(userId)), "已标记");
    }

    /**
     * 通知只能查看本人的：忽略外部传入的 userId，管理员除外
     */
    private Long resolveUserId(Long userId) {
        Long current = com.gyb.xqfx.security.SecurityUtils.currentUserId();
        if (userId != null && com.gyb.xqfx.security.SecurityUtils.hasAnyRole("ADMIN")) {
            return userId;
        }
        return current;
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Integer> markAllRead(@RequestParam Long userId) {
        return Result.success(notifyService.markAllRead(userId), "已全部标记");
    }

    @Operation(summary = "按学期同步预警通知")
    @PostMapping("/sync-warnings")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Map<String, Object>> syncWarnings(@RequestParam(required = false) Long termId) {
        int count = notifyService.syncFromWarnings(termId);
        return Result.success(Map.of("created", count), "已生成 " + count + " 条通知");
    }
}
