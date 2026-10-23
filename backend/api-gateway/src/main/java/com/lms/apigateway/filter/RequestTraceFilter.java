package com.lms.apigateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", id);
        long started = System.nanoTime();
        try (var ignored = MDC.putCloseable("requestId", id)) {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")) log.info(
                "requestId={} method={} path={} status={} durationMs={}",
                id,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                (System.nanoTime() - started) / 1000000
            );
        }
    }
}
