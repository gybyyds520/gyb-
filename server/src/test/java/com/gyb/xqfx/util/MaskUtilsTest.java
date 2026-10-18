package com.gyb.xqfx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏工具测试（参数化驱动）
 */
@DisplayName("数据脱敏工具测试")
class MaskUtilsTest {

    @ParameterizedTest(name = "手机号 {0} -> {1}")
    @CsvSource({
            "13812345678, 138****5678",
            "15900001111, 159****1111",
            "18866668888, 188****8888"
    })
    @DisplayName("手机号脱敏保留前3后4")
    void testMaskPhone(String input, String expected) {
        assertEquals(expected, MaskUtils.maskPhone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"138", "", "1"})
    @DisplayName("手机号长度不足时原样返回")
    void testMaskPhoneShort(String input) {
        assertEquals(input, MaskUtils.maskPhone(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空值安全：null 返回 null，空串返回空串")
    void testMaskPhoneNullOrEmpty(String input) {
        assertEquals(input, MaskUtils.maskPhone(input));
    }

    @ParameterizedTest(name = "身份证 {0} -> {1}")
    @CsvSource({
            "350102200301011234, 350102********1234",
            "11010119900101001X, 110101********001X"
    })
    @DisplayName("身份证脱敏保留前6后4")
    void testMaskIdCard(String input, String expected) {
        assertEquals(expected, MaskUtils.maskIdCard(input));
    }

    @ParameterizedTest(name = "学号 {0} -> {1}")
    @CsvSource({
            "2301132013, 23******13",
            "2301132014, 23******14"
    })
    @DisplayName("学号脱敏保留前2后2")
    void testMaskNo(String input, String expected) {
        assertEquals(expected, MaskUtils.maskNo(input));
    }

    @ParameterizedTest(name = "姓名 {0} -> {1}")
    @CsvSource({
            "张三, 张*",
            "欧阳娜娜, 欧***",
            "李, 李"
    })
    @DisplayName("姓名脱敏保留姓氏")
    void testMaskName(String input, String expected) {
        assertEquals(expected, MaskUtils.maskName(input));
    }

    @ParameterizedTest(name = "邮箱 {0} -> {1}")
    @CsvSource({
            "zhangsan@example.com, z***@example.com",
            "a@example.com, a@example.com",
            "no-at-symbol, no-at-symbol"
    })
    @DisplayName("邮箱脱敏")
    void testMaskEmail(String input, String expected) {
        assertEquals(expected, MaskUtils.maskEmail(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空值不抛异常")
    void testNullSafe(String input) {
        assertDoesNotThrow(() -> {
            MaskUtils.maskPhone(input);
            MaskUtils.maskIdCard(input);
            MaskUtils.maskNo(input);
            MaskUtils.maskName(input);
            MaskUtils.maskEmail(input);
        });
    }
}
