
package com.lms.enrollmentservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "TestSecretKeyAtLeast32BytesLongForHS25612345";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(SECRET);

    private String createToken(String type, long expiryMillis) {
        var key = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", type)
                .claim("role", "STUDENT")
                .issuedAt(new Date(System.currentTimeMillis() - 1000))
                .expiration(new Date(expiryMillis))
                .signWith(key)
                .compact();
    }

    @Test
    void validAccessTokenShouldBeAccepted() {
        String token = createToken(
                "access",
                System.currentTimeMillis() + 60000
        );

        assertTrue(provider.validateToken(token));
    }

    @Test
    void refreshTokenShouldBeRejected() {
        String token = createToken(
                "refresh",
                System.currentTimeMillis() + 60000
        );

        assertFalse(provider.validateToken(token));
    }

    @Test
    void expiredTokenShouldBeRejected() {
        String token = createToken(
                "access",
                System.currentTimeMillis() - 60000
        );

        assertFalse(provider.validateToken(token));
    }

    @Test
    void invalidTokenShouldBeRejected() {
        assertFalse(
                provider.validateToken("invalid.jwt.token")
        );
    }
}
