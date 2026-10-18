package com.gyb.xqfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分片（含向量，用于检索增强）
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    private Long id;

    private Long docId;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private String vectorId;

    /** 向量 JSON（由 schema_phase3.sql 迁移新增） */
    private String embeddingJson;

    private LocalDateTime createTime;

    private Integer isDeleted;
}
