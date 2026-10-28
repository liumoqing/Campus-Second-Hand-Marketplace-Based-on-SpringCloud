package com.campus.mall.common.util;

/**
 * 脱敏工具（01 §9.4）：手机号 138****8888、邮箱 ab***@qq.com。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String name = email.substring(0, at);
        String keep = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return keep + "***" + email.substring(at);
    }
}
