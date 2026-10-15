package com.lms.apigateway.filter;

import com.lms.apigateway.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/*
 * Gateway'in kalbi:
 * 1) /api/v1/internal/** hiçbir zaman dışarıya geçmez.
 * 2) X-User-Id / X-User-Role header'ları istemciden gelirse SİLİNİR —
 *    bu ikisi SADECE doğrulanmış JWT'den türetilir. Aksi halde
 *    kimlik sahteciliği (IDOR) mümkün olurdu.
 * 3) Herkese açık path'ler hariç, geçerli JWT şart.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/courses",
            "/api/v1/sections",
            "/api/v1/lessons",
            "/api/v1/reviews",
            "/api/v1/webhooks/stripe",
            "/actuator/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1) internal path'ler dışarıdan asla erişilemez
        if (path.startsWith("/api/v1/internal/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean isPublic = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);

        String token = extractToken(request);
        UUID userId = null;
        String role = null;
        String name = null;

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            userId = jwtTokenProvider.getUserIdFromToken(token);
            role = jwtTokenProvider.getRoleFromToken(token);
            name = jwtTokenProvider.getNameFromToken(token);
        } else if (!isPublic) {

        }

        HeaderOverrideRequestWrapper wrapped = new HeaderOverrideRequestWrapper(request);
        wrapped.removeHeader("X-User-Id");
        wrapped.removeHeader("X-User-Role");
        wrapped.removeHeader("X-User-Name");   // ← eklendi

        if (userId != null) {
            wrapped.setHeader("X-User-Id", userId.toString());
            wrapped.setHeader("X-User-Role", role);
            if (name != null) wrapped.setHeader("X-User-Name", name);   // ← eklendi
        }
        filterChain.doFilter(wrapped, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}