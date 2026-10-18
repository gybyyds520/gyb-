package com.gyb.xqfx.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 绩点计算测试
 */
@DisplayName("绩点计算测试")
class ScoreServiceImplTest {

    @ParameterizedTest(name = "成绩 {0} -> 绩点 {1}")
    @CsvSource({
            "100, 5.00",
            "95, 4.50",
            "90, 4.00",
            "75, 2.50",
            "60, 1.00",
            "59, 0.00",
            "50, 0.00",
            "30, 0.00",
            "0, 0.00"
    })
    @DisplayName("绩点 = (成绩-50)/10，低于60为0，上限5.0")
    void testCalcGradePoint(String score, String expected) {
        BigDecimal gp = ScoreServiceImpl.calcGradePoint(new BigDecimal(score));
        assertEquals(0, gp.compareTo(new BigDecimal(expected)));
    }

    @ParameterizedTest
    @CsvSource({"100", "99.5", "98"})
    @DisplayName("绩点上限不超过 5.0")
    void testUpperBound(String score) {
        assertTrue(ScoreServiceImpl.calcGradePoint(new BigDecimal(score))
                .compareTo(new BigDecimal("5.00")) <= 0);
    }

    @ParameterizedTest
    @CsvSource({"0", "20", "59"})
    @DisplayName("不及格绩点为 0")
    void testLowerBound(String score) {
        assertEquals(0, ScoreServiceImpl.calcGradePoint(new BigDecimal(score)).compareTo(BigDecimal.ZERO));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("成绩为空返回 0")
    void testNullScore() {
        assertEquals(0, ScoreServiceImpl.calcGradePoint(null).compareTo(BigDecimal.ZERO));
    }
}
