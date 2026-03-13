package com.huah.ai.platform.auth.filter;

import com.huah.ai.platform.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证过滤器
 * 解析 JWT Token，注入 SecurityContext
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtUtil.validateToken(token)) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                String userId = claims.getSubject();
                String department = (String) claims.get("department");
                String rolesStr = (String) claims.get("roles");

                List<SimpleGrantedAuthority> authorities = rolesStr != null
                        ? Arrays.stream(rolesStr.split(","))
                                .map(String::trim)
                                .map(SimpleGrantedAuthority::new)
                                .toList()
                        : List.of();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // 将用户信息传递给下游
                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-Department", department);
                request.setAttribute("X-Roles", rolesStr);

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT认证成功: userId={}, roles={}", userId, rolesStr);
            } catch (Exception e) {
                log.warn("JWT解析失败: {}", e.getMessage());
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
        // 也支持从 Cookie 中读取
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
