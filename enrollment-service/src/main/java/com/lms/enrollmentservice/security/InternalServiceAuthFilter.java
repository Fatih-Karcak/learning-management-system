package com.lms.enrollmentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalServiceAuthFilter
        extends OncePerRequestFilter {

    private final byte[] expectedKey;

    public InternalServiceAuthFilter(
            @Value("${internal.api-key}") String key) {

        if (key == null || key.length() < 32) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY en az 32 karakter olmalı"
            );
        }

        expectedKey = key.getBytes(
                StandardCharsets.UTF_8
        );
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        return !request.getRequestURI()
                .startsWith("/api/v1/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String suppliedKey =
                request.getHeader("X-Internal-Service-Key");

        if (suppliedKey == null ||
                !MessageDigest.isEqual(
                        expectedKey,
                        suppliedKey.getBytes(
                                StandardCharsets.UTF_8
                        ))) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid service credentials"
            );
            return;
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        "internal-service",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_SERVICE"
                                )
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}