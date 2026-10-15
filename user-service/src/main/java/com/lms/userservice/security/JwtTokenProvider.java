package com.lms.userservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(UUID userId, String email, String role,String name) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("name", name)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Geçersiz JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}