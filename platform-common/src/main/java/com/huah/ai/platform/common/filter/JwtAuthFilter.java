package com.huah.ai.platform.common.filter;

import com.huah.ai.platform.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared JWT authentication filter.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_BLACKLIST_PREFIX = "ai:token:blacklist:";
    private static final String TOKEN_JTI_BLACKLIST_PREFIX = "ai:token:blacklist:jti:";
    private static final String TOKEN_REVOKED_RESPONSE =
            "{\"code\":401,\"message\":\"Token 已失效\",\"data\":null}";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtUtil.validateToken(token)) {
            try {
                if (!"access".equals(jwtUtil.getTokenType(token))) {
                    chain.doFilter(request, response);
                    return;
                }

                String jti = jwtUtil.getJti(token);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token))
                        || (jti != null
                                && Boolean.TRUE.equals(
                                        redisTemplate.hasKey(TOKEN_JTI_BLACKLIST_PREFIX + jti)))) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(TOKEN_REVOKED_RESPONSE);
                    return;
                }

                Claims claims = jwtUtil.parseToken(token);
                String subject = claims.getSubject();
                String department = (String) claims.get("department");
                String rolesStr = (String) claims.get("roles");
                Object userIdClaim = claims.get("userId");
                String userId = userIdClaim != null ? userIdClaim.toString() : subject;

                List<SimpleGrantedAuthority> authorities =
                        rolesStr != null
                                ? Arrays.stream(rolesStr.split(","))
                                        .map(String::trim)
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()
                                : List.of();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-Username", subject);
                request.setAttribute("X-Department", department);
                request.setAttribute("X-Roles", rolesStr);

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug(
                        "JWT authentication succeeded: userId={}, username={}, roles={}",
                        userId,
                        subject,
                        rolesStr);
            } catch (Exception e) {
                log.warn("JWT parsing failed: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("ai_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
