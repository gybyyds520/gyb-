package com.gyb.xqfx.util;

import lombok.experimental.UtilityClass;

/**
 * 数据脱敏工具（教育数据合规：手机号、身份证、学号掩码）
 */
@UtilityClass
public class MaskUtils {

    /** 手机号脱敏：138****5678 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证脱敏：保留前 6 位与后 4 位 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        StringBuilder sb = new StringBuilder(idCard.substring(0, 6));
        for (int i = 6; i < idCard.length() - 4; i++) {
            sb.append('*');
        }
        return sb.append(idCard.substring(idCard.length() - 4)).toString();
    }

    /** 学号/工号脱敏：保留前 2 位与后 2 位 */
    public static String maskNo(String no) {
        if (no == null || no.length() < 4) {
            return no;
        }
        StringBuilder sb = new StringBuilder(no.substring(0, 2));
        for (int i = 2; i < no.length() - 2; i++) {
            sb.append('*');
        }
        return sb.append(no.substring(no.length() - 2)).toString();
    }

    /** 姓名脱敏：保留姓氏 */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    /** 邮箱脱敏：a***@example.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String prefix = email.substring(0, at);
        String masked = prefix.length() <= 1 ? prefix : prefix.substring(0, 1) + "***";
        return masked + email.substring(at);
    }
}
