package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 */
@Data
@TableName("audit_log")
public class AuditLog {

    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String url;

    private String params;

    private String ip;

    private String userAgent;

    private Integer costMs;

    private Integer success;

    private String errorMsg;

    private LocalDateTime createTime;
}
