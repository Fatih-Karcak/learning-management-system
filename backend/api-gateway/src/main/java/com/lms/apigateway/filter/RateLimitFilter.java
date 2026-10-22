package com.lms.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // GatewayAuthFilter (order 1) replaces this header using a verified JWT.
        // Students sharing a classroom/NAT must not consume each other's playback quota.
        String userId=request.getHeader("X-User-Id");
        String clientIp = userId==null?"ip:"+request.getRemoteAddr():"user:"+userId;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestLog.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS) {
                response.setContentType("application/json");
                response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                response.setStatus(429);
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"Çok fazla istek, lütfen biraz sonra tekrar deneyin\"}");
                return;
            }
            timestamps.addLast(now);
        }

        filterChain.doFilter(request, response);
    }
}
