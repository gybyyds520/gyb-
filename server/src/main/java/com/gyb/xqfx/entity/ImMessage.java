package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内通讯消息（教师 ↔ 学生 ↔ 家长）
 *
 * <p>通讯范围由 {@link com.gyb.xqfx.service.MessageService#contacts()} 按身份关系推导，
 * 不在通讯录内的账号之间不能互发消息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_message")
public class ImMessage extends BaseEntity {

    /** 发送人（sys_user.id） */
    private Long senderId;

    /** 接收人（sys_user.id） */
    private Long receiverId;

    private String content;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime readTime;
}
