package com.campus.mall.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局鉴权（01 §7.2）：
 * 1. 先剥掉客户端自带的 X-User-Id / X-User-Role，防伪造
 * 2. /inner/** 一律 404（内部接口不过网关）
 * 3. 白名单直接放行（但游客调 /product/page 由服务端强制 20 条）
 * 4. 校验 JWT -> 比对 Redis 里的 token_version -> 查封禁标记
 * 5. 通过后注入 X-User-Id / X-User-Role / X-Trace-Id
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtSupport jwtSupport;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final List<String> whiteList;
    private final boolean authEnabled;

    public AuthGlobalFilter(JwtSupport jwtSupport,
                            ReactiveStringRedisTemplate redis,
                            ObjectMapper objectMapper,
                            @Value("${mall.security.auth-enabled:true}") boolean authEnabled,
                            @Value("${mall.security.white-list:}") String whiteListCsv) {
        this.jwtSupport = jwtSupport;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.authEnabled = authEnabled;
        this.whiteList = (whiteListCsv == null || whiteListCsv.isBlank())
                ? List.of("/auth/captcha", "/auth/register", "/auth/login", "/auth/refresh",
                "/auth/avatar", "/auth/reset-code", "/auth/reset-password",
                "/product/page", "/category/tree", "/payment/callback/**",
                // 图片/头像对外可读（上传出来的静态文件 + 演示头像）
                "/files/**",
                "/static/**",
                // WebSocket 握手用 ?token= 自己验签（浏览器 WS 不能自定义 Authorization 头）
                "/ws/**",
                "/actuator/health", "/favicon.ico")
                : Arrays.stream(whiteListCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 内部接口永不对外
        if (path.startsWith("/inner/")) {
            return writeError(exchange, HttpStatus.NOT_FOUND, 10004, "资源不存在");
        }

        // 剥掉客户端伪造的身份头
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                });
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        builder.header(HEADER_TRACE_ID, traceId);

        // 白名单：不强制登录，但"带了合法 token 就仍然注入身份头"。
        // 否则 /product/page 这类公开接口永远看不到登录用户，首页会一直按游客处理（最多 20 条）。
        boolean white = isWhite(path);
        if (!authEnabled) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String token = bearer(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            if (white) {
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }
            return writeError(exchange, HttpStatus.UNAUTHORIZED, 10002, "未登录或登录已过期");
        }
        Claims claims = jwtSupport.parse(token);
        if (claims == null) {
            if (white) {
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }
            return writeError(exchange, HttpStatus.UNAUTHORIZED, 10002, "未登录或登录已过期");
        }
        String userId = String.valueOf(claims.get("uid"));
        String role = String.valueOf(claims.getOrDefault("role", "USER"));
        String tv = String.valueOf(claims.getOrDefault("tv", "0"));

        // 白名单接口即使 token 已失效（被踢/改角色/过期），也只是降级成游客，不能把公开页打成 401
        if (white) {
            return redis.hasKey("mall:user:banned:" + userId)
                    .flatMap(banned -> {
                        if (Boolean.TRUE.equals(banned)) {
                            return chain.filter(exchange.mutate().request(builder.build()).build());
                        }
                        return redis.opsForValue().get("mall:user:tv:" + userId)
                                .defaultIfEmpty(tv)
                                .flatMap(currentTv -> {
                                    ServerHttpRequest req = tv.equals(currentTv)
                                            ? builder.header(HEADER_USER_ID, userId).header(HEADER_USER_ROLE, role).build()
                                            : builder.build();
                                    return chain.filter(exchange.mutate().request(req).build());
                                });
                    });
        }

        return redis.hasKey("mall:user:banned:" + userId)
                .flatMap(banned -> {
                    if (Boolean.TRUE.equals(banned)) {
                        return redis.opsForValue().get("mall:user:banned:" + userId)
                                .defaultIfEmpty("{}")
                                .flatMap(v -> writeError(exchange, HttpStatus.FORBIDDEN, 20003,
                                        "您因违反用户规则已被封禁", v));
                    }
                    return redis.opsForValue().get("mall:user:tv:" + userId)
                            .defaultIfEmpty(tv)
                            .flatMap(currentTv -> {
                                if (!currentTv.equals(tv)) {
                                    return writeError(exchange, HttpStatus.UNAUTHORIZED, 10002, "登录状态已失效，请重新登录");
                                }
                                ServerHttpRequest req = builder
                                        .header(HEADER_USER_ID, userId)
                                        .header(HEADER_USER_ROLE, role)
                                        .build();
                                return chain.filter(exchange.mutate().request(req).build());
                            });
                });
    }

    private boolean isWhite(String path) {
        return whiteList.stream().anyMatch(p -> p.equals(path) || MATCHER.match(p, path));
    }

    private String bearer(String header) {
        if (header == null) {
            return null;
        }
        String prefix = "Bearer ";
        return header.startsWith(prefix) ? header.substring(prefix.length()).trim() : header.trim();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        return writeError(exchange, status, code, message, null);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", data);
        body.put("timestamp", System.currentTimeMillis());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
