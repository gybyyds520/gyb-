package com.gyb.xqfx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据脱敏工具数据驱动测试：覆盖不同长度、字符集与边界输入
 */
@DisplayName("数据脱敏数据驱动测试")
class MaskUtilsDataDrivenTest {

    private static final String DIGITS = "0123456789";
    private static final String ALPHA = "abcdefghijklmnopqrstuvwxyz";

    /** 构造指定长度的示例串 */
    private static String build(int length, String alphabet, int seed) {
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // ==================== 手机号 ====================

    static Stream<Arguments> phones() {
        List<Arguments> args = new ArrayList<>();
        for (int len = 0; len <= 20; len++) {
            for (int seed = 0; seed < 5; seed++) {
                args.add(Arguments.of(build(len, DIGITS, seed)));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "手机号脱敏 [{0}]")
    @MethodSource("phones")
    @DisplayName("手机号脱敏保留前3后4或原样返回")
    void testMaskPhone(String phone) {
        String result = MaskUtils.maskPhone(phone);
        if (phone == null || phone.length() < 7) {
            assertEquals(phone, result);
            return;
        }
        assertTrue(result.startsWith(phone.substring(0, 3)), "应保留前3位");
        assertTrue(result.endsWith(phone.substring(phone.length() - 4)), "应保留后4位");
        assertTrue(result.contains("****"), "应包含掩码");
    }

    static Stream<Arguments> phonesIdempotent() {
        List<Arguments> args = new ArrayList<>();
        for (int len = 7; len <= 20; len += 1) {
            args.add(Arguments.of(build(len, DIGITS, len)));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "手机号脱敏幂等 [{0}]")
    @MethodSource("phonesIdempotent")
    @DisplayName("重复脱敏不报错且仍含掩码")
    void testMaskPhoneIdempotent(String phone) {
        String once = MaskUtils.maskPhone(phone);
        String twice = MaskUtils.maskPhone(once);
        assertNotNull(twice);
        assertTrue(twice.contains("****"));
    }

    // ==================== 身份证 ====================

    static Stream<Arguments> idCards() {
        List<Arguments> args = new ArrayList<>();
        for (int len = 0; len <= 20; len++) {
            for (int seed = 0; seed < 5; seed++) {
                args.add(Arguments.of(build(len, DIGITS + "X", seed)));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "身份证脱敏 [{0}]")
    @MethodSource("idCards")
    @DisplayName("身份证脱敏保留前6后4且长度不变")
    void testMaskIdCard(String idCard) {
        String result = MaskUtils.maskIdCard(idCard);
        if (idCard == null || idCard.length() < 10) {
            assertEquals(idCard, result);
            return;
        }
        assertEquals(idCard.length(), result.length(), "长度应保持不变");
        assertTrue(result.startsWith(idCard.substring(0, 6)), "应保留前6位");
        assertTrue(result.endsWith(idCard.substring(idCard.length() - 4)), "应保留后4位");
        for (int i = 6; i < result.length() - 4; i++) {
            assertEquals('*', result.charAt(i), "第 " + i + " 位应为掩码");
        }
    }

    // ==================== 学号工号 ====================

    static Stream<Arguments> nos() {
        List<Arguments> args = new ArrayList<>();
        for (int len = 0; len <= 20; len++) {
            for (int seed = 0; seed < 5; seed++) {
                args.add(Arguments.of(build(len, DIGITS, seed)));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "学号脱敏 [{0}]")
    @MethodSource("nos")
    @DisplayName("学号脱敏保留前2后2且长度不变")
    void testMaskNo(String no) {
        String result = MaskUtils.maskNo(no);
        if (no == null || no.length() < 4) {
            assertEquals(no, result);
            return;
        }
        assertEquals(no.length(), result.length());
        assertTrue(result.startsWith(no.substring(0, 2)));
        assertTrue(result.endsWith(no.substring(no.length() - 2)));
        for (int i = 2; i < result.length() - 2; i++) {
            assertEquals('*', result.charAt(i));
        }
    }

    // ==================== 姓名 ====================

    static Stream<Arguments> names() {
        List<Arguments> args = new ArrayList<>();
        for (int len = 1; len <= 12; len++) {
            for (int seed = 0; seed < 6; seed++) {
                args.add(Arguments.of(build(len, ALPHA, seed)));
            }
        }
        args.add(Arguments.of((String) null));
        args.add(Arguments.of(""));
        return args.stream();
    }

    @ParameterizedTest(name = "姓名脱敏 [{0}]")
    @MethodSource("names")
    @DisplayName("姓名脱敏保留姓氏且长度不变")
    void testMaskName(String name) {
        String result = MaskUtils.maskName(name);
        if (name == null || name.length() <= 1) {
            assertEquals(name, result);
            return;
        }
        assertEquals(name.length(), result.length());
        assertEquals(name.charAt(0), result.charAt(0), "应保留姓氏");
        for (int i = 1; i < result.length(); i++) {
            assertEquals('*', result.charAt(i));
        }
    }

    // ==================== 邮箱 ====================

    static Stream<Arguments> emails() {
        List<Arguments> args = new ArrayList<>();
        String[] domains = {"@example.com", "@x.io", "@a"};
        String[] prefixes = {"", "a", "ab", "zhang", "very-long-prefix-value", "user.name"};
        for (String p : prefixes) {
            for (String d : domains) {
                args.add(Arguments.of(p + d));
            }
        }
        args.add(Arguments.of("no-at-symbol"));
        args.add(Arguments.of(""));
        args.add(Arguments.of((String) null));
        return args.stream();
    }

    @ParameterizedTest(name = "邮箱脱敏 [{0}]")
    @MethodSource("emails")
    @DisplayName("邮箱脱敏保留域名")
    void testMaskEmail(String email) {
        String result = MaskUtils.maskEmail(email);
        if (email == null || !email.contains("@")) {
            assertEquals(email, result);
            return;
        }
        int at = email.indexOf('@');
        int atResult = result.indexOf('@');
        assertTrue(atResult >= 0, "结果应包含 @");
        assertEquals(email.substring(at), result.substring(atResult), "域名应保持一致");
    }
}
