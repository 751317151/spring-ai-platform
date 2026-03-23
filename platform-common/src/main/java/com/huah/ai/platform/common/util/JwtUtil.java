package com.huah.ai.platform.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类。
 */
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(String secret, long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, expirationMs, "access", UUID.randomUUID().toString());
    }

    public String generateToken(String subject,
                                Map<String, Object> claims,
                                long ttlMs,
                                String tokenType,
                                String jti) {
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .id(jti)
                .claim("tokenType", tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("无效 JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    public Object getClaim(String token, String key) {
        return parseToken(token).get(key);
    }

    public String getTokenType(String token) {
        Object tokenType = getClaim(token, "tokenType");
        return tokenType != null ? tokenType.toString() : "access";
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }
}
