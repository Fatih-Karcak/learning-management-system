package com.lms.enrollmentservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final Set<String> ROLES =
            Set.of("STUDENT", "INSTRUCTOR", "ADMIN");

    private final SecretKey key;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret) {

        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);

            UUID.fromString(claims.getSubject());

            String type =
                    claims.get("type", String.class);

            String role =
                    claims.get("role", String.class);

            return "access".equals(type)
                    && ROLES.contains(role)
                    && claims.getExpiration() != null
                    && claims.getIssuedAt() != null;

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(
                parseToken(token).getSubject()
        );
    }

    public String getRoleFromToken(String token) {
        return parseToken(token)
                .get("role", String.class);
    }
}