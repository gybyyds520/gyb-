package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内消息通知
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_message")
public class NotifyMessage extends BaseEntity {

    private Long userId;

    /** WARNING / INTERVENTION / SYSTEM / IMPORT */
    private String messageType;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    /** IN_APP 站内 / SMS 短信 / EMAIL 邮件 */
    private String channel = "IN_APP";

    private Integer isRead = 0;

    private LocalDateTime readTime;
}
