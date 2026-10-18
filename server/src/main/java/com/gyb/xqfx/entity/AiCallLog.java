package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型调用日志（审计与成本统计）
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    private Long id;

    /** REPORT / ADVICE / CHAT */
    private String bizType;

    private Long bizId;

    private String modelName;

    private Long templateId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer costMs;

    /** SUCCESS / FAILED / FALLBACK */
    private String status;

    private String errorMsg;

    private Long userId;

    private LocalDateTime createTime;
}
