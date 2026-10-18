package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.entity.KnowledgeChunk;
import com.gyb.xqfx.mapper.KnowledgeChunkMapper;
import com.gyb.xqfx.mapper.KnowledgeDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 知识库与 RAG 检索测试
 */
@DisplayName("知识库检索服务测试")
class KnowledgeServiceTest {

    private KnowledgeDocMapper docMapper;
    private KnowledgeChunkMapper chunkMapper;
    private OllamaClient ollamaClient;
    private KnowledgeService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        docMapper = Mockito.mock(KnowledgeDocMapper.class);
        chunkMapper = Mockito.mock(KnowledgeChunkMapper.class);
        ollamaClient = Mockito.mock(OllamaClient.class);
        service = new KnowledgeService(docMapper, chunkMapper, ollamaClient, objectMapper);
    }

    // ==================== 文本切分 ====================

    @Test
    @DisplayName("空文本切分为空列表")
    void testSplitEmpty() {
        assertTrue(service.split("").isEmpty());
        assertTrue(service.split(null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 499, 500, 501, 1200})
    @DisplayName("切分后总长度应与原文一致")
    void testSplitPreservesContent(int length) {
        String text = "a".repeat(length);
        List<String> chunks = service.split(text);
        assertEquals(length, chunks.stream().mapToInt(String::length).sum());
        assertFalse(chunks.isEmpty());
    }

    @Test
    @DisplayName("超过阈值时应切分为多片")
    void testSplitMultiple() {
        List<String> chunks = service.split("x".repeat(1200));
        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).length());
        assertEquals(200, chunks.get(2).length());
    }

    // ==================== 余弦相似度 ====================

    @ParameterizedTest(name = "向量 {0} 与 {1} 相似度 {2}")
    @CsvSource({
            "1,1,1.0",
            "1,-1,-1.0",
            "1,0,0.0"
    })
    @DisplayName("基础向量相似度计算")
    void testCosineSimple(double a, double b, double expected) {
        assertEquals(expected, service.cosine(List.of(a, 0.0), List.of(b, 0.0)), 1e-9);
    }

    @Test
    @DisplayName("相同向量相似度为 1")
    void testCosineIdentical() {
        List<Double> v = List.of(0.3, 0.4, 0.5);
        assertEquals(1.0, service.cosine(v, v), 1e-9);
    }

    @Test
    @DisplayName("正交向量相似度为 0")
    void testCosineOrthogonal() {
        assertEquals(0.0, service.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0)), 1e-9);
    }

    @Test
    @DisplayName("长度不一致或空向量返回 0")
    void testCosineInvalid() {
        assertEquals(0.0, service.cosine(List.of(1.0), List.of(1.0, 2.0)));
        assertEquals(0.0, service.cosine(List.of(), List.of()));
        assertEquals(0.0, service.cosine(null, List.of(1.0)));
        assertEquals(0.0, service.cosine(List.of(0.0, 0.0), List.of(1.0, 1.0)));
    }

    // ==================== 检索 ====================

    private KnowledgeChunk chunk(String content, String embedding) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setContent(content);
        c.setEmbeddingJson(embedding);
        return c;
    }

    @Test
    @DisplayName("向量可用时按相似度降序返回")
    void testSearchByVector() throws Exception {
        Mockito.when(ollamaClient.embed("奖学金")).thenReturn(List.of(1.0, 0.0));
        Mockito.when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk("成绩评定办法", objectMapper.writeValueAsString(List.of(0.0, 1.0))),
                chunk("奖学金评选细则", objectMapper.writeValueAsString(List.of(1.0, 0.0))),
                chunk("违纪处分规定", objectMapper.writeValueAsString(List.of(-1.0, 0.0)))
        ));

        // 仅「奖学金评选细则」与查询向量同向，「违纪处分规定」为负相关应被过滤
        List<String> results = service.search("奖学金", 2);
        assertEquals(1, results.size());
        assertEquals("奖学金评选细则", results.get(0));
    }

    @Test
    @DisplayName("向量不可用时退化为关键词匹配")
    void testSearchFallbackKeyword() {
        Mockito.when(ollamaClient.embed(any())).thenReturn(null);
        Mockito.when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk("奖学金评选细则", null),
                chunk("考勤管理办法", null)
        ));

        List<String> results = service.search("奖学金", 5);
        assertEquals(1, results.size());
        assertTrue(results.get(0).contains("奖学金"));
    }

    @Test
    @DisplayName("无匹配时返回空列表")
    void testSearchNoMatch() {
        Mockito.when(ollamaClient.embed(any())).thenReturn(List.of(1.0, 0.0));
        Mockito.when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk("无关内容", "[0.0,-1.0]")
        ));
        assertTrue(service.search("奖学金", 3).isEmpty());
    }

    @Test
    @DisplayName("topK 限制返回条数")
    void testSearchTopK() throws Exception {
        Mockito.when(ollamaClient.embed(any())).thenReturn(List.of(1.0, 0.0));
        Mockito.when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk("A", objectMapper.writeValueAsString(List.of(1.0, 0.0))),
                chunk("B", objectMapper.writeValueAsString(List.of(0.9, 0.1))),
                chunk("C", objectMapper.writeValueAsString(List.of(0.8, 0.2)))
        ));
        assertEquals(2, service.search("x", 2).size());
    }
}
