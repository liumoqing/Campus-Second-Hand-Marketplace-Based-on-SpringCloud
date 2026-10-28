package com.campus.mall.common.security;

import com.campus.mall.common.constant.MallConstants;

/**
 * 当前登录用户上下文，由网关解出的请求头填充（见 {@link UserContextFilter}）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String role) {
        HOLDER.set(new LoginUser(userId, role));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw new com.campus.mall.common.exception.BizException(
                    com.campus.mall.common.api.ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static String role() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.role();
    }

    public static boolean isLogin() {
        return userId() != null;
    }

    public static boolean isAdmin() {
        String r = role();
        return MallConstants.ROLE_ADMIN.equals(r) || MallConstants.ROLE_SUPER_ADMIN.equals(r);
    }

    public static boolean isSuperAdmin() {
        return MallConstants.ROLE_SUPER_ADMIN.equals(role());
    }

    public record LoginUser(Long userId, String role) {
    }
}
