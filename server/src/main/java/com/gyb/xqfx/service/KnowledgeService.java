package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.entity.KnowledgeChunk;
import com.gyb.xqfx.entity.KnowledgeDoc;
import com.gyb.xqfx.mapper.KnowledgeChunkMapper;
import com.gyb.xqfx.mapper.KnowledgeDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知识库与检索增强（RAG）：文档分片 → 向量化 → 余弦相似度检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /** 单个分片最大字符数 */
    private static final int CHUNK_SIZE = 500;

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    /**
     * 录入文档并构建向量索引
     *
     * @return 文档ID
     */
    public Long addDoc(String docName, String docType, String content) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocName(docName);
        doc.setDocType(docType == null ? "OTHER" : docType);
        doc.setContentHash(sha256(content));
        doc.setChunkCount(0);
        doc.setStatus("PENDING");
        docMapper.insert(doc);

        List<String> chunks = split(content);
        List<KnowledgeChunk> entities = new ArrayList<>();
        int index = 0;
        for (String chunk : chunks) {
            KnowledgeChunk kc = new KnowledgeChunk();
            kc.setDocId(doc.getId());
            kc.setChunkIndex(index++);
            kc.setContent(chunk);
            kc.setTokenCount(chunk.length());
            kc.setCreateTime(LocalDateTime.now());
            kc.setIsDeleted(0);
            List<Double> vec = ollamaClient.embed(chunk);
            if (vec != null) {
                try {
                    kc.setEmbeddingJson(objectMapper.writeValueAsString(vec));
                } catch (Exception e) {
                    log.warn("向量序列化失败：{}", e.getMessage());
                }
            }
            entities.add(kc);
        }
        if (!entities.isEmpty()) {
            chunkMapper.insertBatchSomeColumn(entities);
        }

        doc.setChunkCount(entities.size());
        doc.setStatus("INDEXED");
        docMapper.updateById(doc);
        return doc.getId();
    }

    /**
     * 检索与问题最相关的分片
     *
     * @param topK 返回条数
     */
    public List<String> search(String query, int topK) {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(null);
        List<Double> queryVec = ollamaClient.embed(query);

        // 无向量能力时退化为关键词匹配
        if (queryVec == null) {
            return chunks.stream()
                    .filter(c -> c.getContent() != null && c.getContent().contains(query))
                    .map(KnowledgeChunk::getContent)
                    .limit(topK)
                    .toList();
        }

        return chunks.stream()
                .filter(c -> c.getEmbeddingJson() != null)
                .map(c -> new ScoredChunk(c.getContent(), cosine(queryVec, parseVector(c.getEmbeddingJson()))))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .map(ScoredChunk::content)
                .toList();
    }

    /** 文档列表 */
    public List<KnowledgeDoc> listDocs() {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .orderByDesc(KnowledgeDoc::getId));
    }

    /** 删除文档及其分片 */
    public void deleteDoc(Long docId) {
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
        docMapper.deleteById(docId);
    }

    /** 按固定长度切分文本 */
    public List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return chunks;
        }
        int len = content.length();
        for (int i = 0; i < len; i += CHUNK_SIZE) {
            chunks.add(content.substring(i, Math.min(i + CHUNK_SIZE, len)));
        }
        return chunks;
    }

    /** 余弦相似度 */
    public double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> parseVector(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    private record ScoredChunk(String content, double score) {
    }
}
