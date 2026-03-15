package com.huah.ai.platform.common.filter;

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
 * 通用 JWT 认证过滤器
 * 解析 JWT Token，注入 SecurityContext，设置请求属性
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
                String subject = claims.getSubject();
                String department = (String) claims.get("department");
                String rolesStr = (String) claims.get("roles");
                // 优先使用 claims 中的 userId（UUID），回退到 subject（username）
                Object userIdClaim = claims.get("userId");
                String userId = userIdClaim != null ? userIdClaim.toString() : subject;

                List<SimpleGrantedAuthority> authorities = rolesStr != null
                        ? Arrays.stream(rolesStr.split(","))
                                .map(String::trim)
                                .map(SimpleGrantedAuthority::new)
                                .toList()
                        : List.of();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // 将用户信息传递给下游 Controller
                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-Username", subject);
                request.setAttribute("X-Department", department);
                request.setAttribute("X-Roles", rolesStr);

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT 认证成功: userId={}, username={}, roles={}", userId, subject, rolesStr);
            } catch (Exception e) {
                log.warn("JWT 解析失败: {}", e.getMessage());
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
