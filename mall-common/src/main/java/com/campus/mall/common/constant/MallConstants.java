package com.campus.mall.common.constant;

/**
 * 全项目常量：请求头、角色、缓存 key 前缀。
 */
public final class MallConstants {

    private MallConstants() {
    }

    // ---------- 请求头（网关注入，服务端只读） ----------
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    /** 服务间内部调用凭据（/inner/** 必须带） */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    /** MDC 中 traceId 的 key（日志 pattern 里用 %X{traceId:-}） */
    public static final String MDC_TRACE_ID = "traceId";

    // ---------- 角色（与 02 t_user.role 一致） ----------
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    // ---------- 账号状态（02 t_user.status） ----------
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_BANNED = "BANNED";

    /** 永久封禁/永久限制上架的解封时间哨兵值（02 t_user.ban_until 约定） */
    public static final java.time.LocalDateTime PERMANENT_UNTIL =
            java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    // ---------- Redis key 前缀（与 01 §7.9 一致） ----------
    public static final String CACHE_USER_TV = "mall:user:tv:";
    public static final String CACHE_USER_BANNED = "mall:user:banned:";
    public static final String CACHE_USER_INFO = "mall:user:info:";
    public static final String CACHE_CATEGORY_TREE = "mall:category:tree";
    public static final String CACHE_PRODUCT_DETAIL = "mall:product:detail:";
    public static final String CACHE_PRODUCT_HOT = "mall:product:hot";
    public static final String CACHE_MSG_UNREAD = "mall:msg:unread:";
    public static final String CACHE_MSG_ONLINE = "mall:msg:online:";
    public static final String CACHE_AUTH_RESET = "mall:auth:reset:";
    public static final String CACHE_CAPTCHA = "mall:auth:captcha:";
    public static final String CACHE_REFRESH_TOKEN = "mall:auth:refresh:";

    /** 未登录也可访问的路径（网关白名单，精确路径 + 方法） */
    public static final String[] WHITE_LIST = {
            "/auth/captcha", "/auth/register", "/auth/login", "/auth/refresh",
            "/auth/avatar", "/auth/reset-code", "/auth/reset-password",
            "/product/page", "/category/tree",
            "/payment/callback/**",
            "/doc/**", "/v3/api-docs/**", "/webjars/**", "/favicon.ico",
            "/actuator/health"
    };
}
