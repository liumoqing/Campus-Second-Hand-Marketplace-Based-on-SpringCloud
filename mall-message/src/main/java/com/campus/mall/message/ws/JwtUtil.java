package com.campus.mall.message.ws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/** 只做一件事：把握手带进来的 token 解析成 userId（role/tv 不在这里校验，网关已校验）。 */
@Component
public class JwtUtil {

    @Value("${mall.jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析失败（过期/签名不对/不是 access 令牌）一律返回 null，由拦截器拒绝握手 */
    public Long userIdOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String type = claims.get("type", String.class);
            if (type != null && !"access".equals(type)) {
                return null;
            }
            Object uid = claims.get("uid");
            return uid == null ? null : Long.valueOf(String.valueOf(uid));
        } catch (Exception e) {
            return null;
        }
    }
}
