package com.gyb.xqfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyb.xqfx.client.OllamaClient;
import com.gyb.xqfx.mapper.KnowledgeChunkMapper;
import com.gyb.xqfx.mapper.KnowledgeDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量相似度数据驱动测试：余弦相似度性质在随机向量上应恒成立
 */
@DisplayName("向量相似度数据驱动测试")
class KnowledgeVectorDataDrivenTest {

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(
                Mockito.mock(KnowledgeDocMapper.class),
                Mockito.mock(KnowledgeChunkMapper.class),
                Mockito.mock(OllamaClient.class),
                new ObjectMapper());
    }

    private static List<Double> randomVector(int dim, long seed) {
        Random random = new Random(seed);
        List<Double> vec = new ArrayList<>();
        for (int i = 0; i < dim; i++) {
            vec.add(random.nextDouble() * 2 - 1);
        }
        return vec;
    }

    /** 生成 (向量A, 向量B) 组合 */
    static Stream<Arguments> vectorPairs() {
        List<Arguments> args = new ArrayList<>();
        for (int dim : new int[]{2, 4, 8, 16}) {
            for (long seed = 0; seed < 12; seed++) {
                args.add(Arguments.of(randomVector(dim, seed), randomVector(dim, seed + 1000)));
            }
        }
        return args.stream();
    }

    static Stream<Arguments> singleVectors() {
        List<Arguments> args = new ArrayList<>();
        for (int dim : new int[]{1, 2, 3, 5, 8, 16, 32}) {
            for (long seed = 0; seed < 10; seed++) {
                args.add(Arguments.of(randomVector(dim, seed)));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "自相似度 dim={0}")
    @MethodSource("singleVectors")
    @DisplayName("任意向量与自身相似度为 1")
    void testSelfSimilarity(List<Double> v) {
        assertEquals(1.0, service.cosine(v, v), 1e-9);
    }

    @ParameterizedTest(name = "相反向量 dim={0}")
    @MethodSource("singleVectors")
    @DisplayName("向量与相反向量相似度为 -1")
    void testOppositeSimilarity(List<Double> v) {
        List<Double> negated = v.stream().map(x -> -x).toList();
        assertEquals(-1.0, service.cosine(v, negated), 1e-9);
    }

    @ParameterizedTest(name = "相似度对称性 dim={0}")
    @MethodSource("vectorPairs")
    @DisplayName("余弦相似度满足对称性")
    void testSymmetry(List<Double> a, List<Double> b) {
        assertEquals(service.cosine(a, b), service.cosine(b, a), 1e-9);
    }

    @ParameterizedTest(name = "相似度有界 dim={0}")
    @MethodSource("vectorPairs")
    @DisplayName("余弦相似度取值在 [-1,1]")
    void testBounded(List<Double> a, List<Double> b) {
        double score = service.cosine(a, b);
        assertTrue(score >= -1.0 - 1e-9 && score <= 1.0 + 1e-9, "相似度为 " + score);
    }

    @ParameterizedTest(name = "缩放不变性 dim={0}")
    @MethodSource("vectorPairs")
    @DisplayName("向量等比缩放不改变相似度")
    void testScaleInvariant(List<Double> a, List<Double> b) {
        double origin = service.cosine(a, b);
        List<Double> scaled = b.stream().map(x -> x * 7.5).toList();
        assertEquals(origin, service.cosine(a, scaled), 1e-9);
    }

    @ParameterizedTest(name = "非法输入 dim={0}")
    @MethodSource("singleVectors")
    @DisplayName("维度不一致或零向量返回 0")
    void testInvalidInputs(List<Double> v) {
        List<Double> longer = new ArrayList<>(v);
        longer.add(1.0);
        assertEquals(0.0, service.cosine(v, longer));
        assertEquals(0.0, service.cosine(v, v.stream().map(x -> 0.0).toList()));
        assertEquals(0.0, service.cosine(v, List.of()));
        assertEquals(0.0, service.cosine(null, v));
    }
}
