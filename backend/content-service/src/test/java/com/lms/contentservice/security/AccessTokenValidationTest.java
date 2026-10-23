package com.lms.contentservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenValidationTest {
    private static final String SECRET = "LocalRegressionSigningKeyAtLeast32BytesLong12345";
    private JwtTokenProvider provider;
    @BeforeEach void setup() { provider = new JwtTokenProvider();
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "jwtSecret", SECRET); }

    private String token(String type, String role, String subject, boolean issued, Date expiry) {
        var builder = Jwts.builder().subject(subject).claim("type", type).claim("role", role);
        if (issued) builder.issuedAt(new Date(System.currentTimeMillis() - 1000));
        if (expiry != null) builder.expiration(expiry);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private Date future() { return new Date(System.currentTimeMillis() + 60000); }
    @Test void acceptsAccessToken() {
        assertTrue(provider.validateToken(token("access", "STUDENT", UUID.randomUUID().toString(), true, future())));
    }
    @Test void rejectsRefreshToken() {
        assertFalse(provider.validateToken(token("refresh", null, UUID.randomUUID().toString(), true, future())));
    }
    @Test void rejectsInvalidRole() {
        assertFalse(provider.validateToken(token("access", "SERVICE", UUID.randomUUID().toString(), true, future())));
        assertFalse(provider.validateToken(token("access", null, UUID.randomUUID().toString(), true, future())));
    }
    @Test void rejectsMissingOrInvalidSubjectWithoutThrowing() {
        assertFalse(provider.validateToken(token("access", "STUDENT", null, true, future())));
        assertFalse(provider.validateToken(token("access", "STUDENT", "not-a-uuid", true, future())));
    }
    @Test void requiresIssuedAtAndExpiry() {
        assertFalse(provider.validateToken(token("access", "STUDENT", UUID.randomUUID().toString(), false, future())));
        assertFalse(provider.validateToken(token("access", "STUDENT", UUID.randomUUID().toString(), true, null)));
    }
    @Test void rejectsExpiredToken() {
        assertFalse(provider.validateToken(token("access", "STUDENT", UUID.randomUUID().toString(), true, new Date(1))));
    }
}
