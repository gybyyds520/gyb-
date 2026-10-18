package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    private String docName;

    /** POLICY / COURSE / FAQ / OTHER */
    private String docType;

    private String fileUrl;

    private String contentHash;

    private Integer chunkCount;

    /** PENDING / INDEXED / FAILED */
    private String status;

    private Long uploaderId;
}
