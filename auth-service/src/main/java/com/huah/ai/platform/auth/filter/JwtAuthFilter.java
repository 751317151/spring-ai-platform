package com.huah.ai.platform.auth.filter;

import com.huah.ai.platform.common.util.JwtUtil;

/**
 * 兼容桥接 - 委托给 platform-common 的通用 JwtAuthFilter
 */
public class JwtAuthFilter extends com.huah.ai.platform.common.filter.JwtAuthFilter {
    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(jwtUtil);
    }
}
