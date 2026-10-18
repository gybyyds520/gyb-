package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_template")
public class AiPromptTemplate extends BaseEntity {

    private String templateCode;

    private String templateName;

    /** REPORT / ADVICE / CHAT / TAG */
    private String scene;

    /** 模板内容，变量用 {xxx} 占位 */
    private String content;

    /** 变量说明 JSON */
    private String variablesJson;

    private Integer version;

    private Integer enabled;
}
