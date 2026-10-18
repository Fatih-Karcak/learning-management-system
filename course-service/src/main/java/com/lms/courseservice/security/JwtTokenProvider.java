package com.lms.courseservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            String role = claims.get("role", String.class);
            if (claims.getSubject() == null || role == null) return false;
            UUID.fromString(claims.getSubject());
            return "access".equals(claims.get("type", String.class))
                    && role != null
                    && java.util.Set.of("STUDENT", "INSTRUCTOR", "ADMIN").contains(role)
                    && claims.getExpiration() != null && claims.getIssuedAt() != null;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Geçersiz JWT token: {}", e.getMessage());
            return false;
        }
    }
}