package com.gyb.xqfx.controller;

import com.gyb.xqfx.annotation.RequireRole;
import com.gyb.xqfx.common.Result;
import com.gyb.xqfx.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通讯：教师 ↔ 学生 ↔ 家长
 *
 * <p>三类角色共用同一套接口，可联系对象由 {@link MessageService#contacts()} 按身份关系推导，
 * 越权发消息会被拒绝。</p>
 */
@Tag(name = "站内通讯")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "我的通讯录")
    @GetMapping("/contacts")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<List<MessageService.Contact>> contacts() {
        return Result.success(messageService.contacts());
    }

    @Operation(summary = "会话列表（含未读数）")
    @GetMapping("/conversations")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<List<MessageService.Conversation>> conversations() {
        return Result.success(messageService.conversations());
    }

    @Operation(summary = "与某人的聊天记录")
    @GetMapping("/history")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<List<MessageService.MessageVO>> history(@RequestParam Long peerUserId,
                                                          @RequestParam(required = false) Integer limit) {
        return Result.success(messageService.history(peerUserId, limit));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<MessageService.MessageVO> send(@RequestBody SendRequest req) {
        return Result.success(messageService.send(req.getReceiverUserId(), req.getContent()), "已发送");
    }

    @Operation(summary = "将与某人的会话标为已读")
    @PostMapping("/read")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Map<String, Object>> read(@RequestParam Long peerUserId) {
        int n = messageService.markRead(peerUserId);
        return Result.success(Map.of("readCount", n), n > 0 ? "已标记 " + n + " 条为已读" : "没有未读消息");
    }

    @Operation(summary = "未读消息总数")
    @GetMapping("/unread")
    @RequireRole({"ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER", "STUDENT", "PARENT"})
    public Result<Long> unread() {
        return Result.success(messageService.unreadTotal());
    }

    @Data
    public static class SendRequest {
        private Long receiverUserId;
        private String content;
    }
}
