package com.campus.mall.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发/解析：access 带 uid/role/tv/jti，refresh 带 uid/tv/type=refresh（03 §1.10、01 §7.3）。
 * 网关只验签 + 比对 tv，不查库。
 */
@Component
@Getter
public class JwtUtil {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TV = "tv";
    public static final String CLAIM_TYPE = "type";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtUtil(@Value("${mall.jwt.secret}") String secret,
                   @Value("${mall.jwt.access-token-minutes:120}") long accessMinutes,
                   @Value("${mall.jwt.refresh-token-days:7}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String accessToken(Long userId, String role, int tv) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TV, tv)
                .claim(CLAIM_TYPE, "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public String refreshToken(Long userId, int tv) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_TV, tv)
                .claim(CLAIM_TYPE, "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long accessSeconds() {
        return accessTtl.toSeconds();
    }
}
