package com.lms.userservice.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final JdbcTemplate jdbc;

    @Value("${jwt.refresh-token-expiration}")
    private long lifetime;

    public record Issued(UUID id, UUID userId, String refreshToken) {}

    private String random() {
        byte[] b = new byte[48];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String s) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Issued create(UUID user) {
        var id = UUID.randomUUID();
        String token = random();
        jdbc.update(
            "insert into user_sessions(id,user_id,refresh_hash,expires_at) values(?,?,?,?)",
            id,
            user,
            hash(token),
            LocalDateTime.now(java.time.Clock.systemUTC()).plusNanos(lifetime * 1000000)
        );
        return new Issued(id, user, token);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Issued rotate(String raw) {
        if (raw == null || raw.length() > 256) throw new BadCredentialsException("Oturum geçersiz");
        String h = hash(raw);
        var rows = jdbc.queryForList(
            "select id,user_id,expires_at,revoked from user_sessions where refresh_hash=? for update",
            h
        );
        if (rows.isEmpty()) {
            jdbc.update(
                "update user_sessions set revoked=true where id in(select session_id from used_refresh_tokens where token_hash=?)",
                h
            );
            throw new BadCredentialsException("Oturum geçersiz veya daha önce yenilendi");
        }
        var r = rows.getFirst();
        if (
            Boolean.TRUE.equals(r.get("revoked")) ||
            ((java.sql.Timestamp) r.get("expires_at"))
                .toLocalDateTime()
                .isBefore(LocalDateTime.now(java.time.Clock.systemUTC()))
        ) throw new BadCredentialsException("Oturum sona erdi");
        var id = (UUID) r.get("id");
        String next = random();
        jdbc.update("insert into used_refresh_tokens(token_hash,session_id) values(?,?)", h, id);
        jdbc.update("update user_sessions set refresh_hash=?,last_seen_at=now() where id=?", hash(next), id);
        return new Issued(id, (UUID) r.get("user_id"), next);
    }

    public boolean active(UUID user, UUID id) {
        return (
            id != null &&
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from user_sessions where id=? and user_id=? and not revoked and expires_at>now())",
                    Boolean.class,
                    id,
                    user
                )
            )
        );
    }

    public List<Map<String, Object>> list(UUID user) {
        return jdbc.queryForList(
            "select id,created_at,last_seen_at,expires_at from user_sessions where user_id=? and not revoked and expires_at>now() order by created_at desc limit 50",
            user
        );
    }

    public void revoke(UUID user, UUID id) {
        jdbc.update("update user_sessions set revoked=true where user_id=? and id=?", user, id);
    }

    public void revokeAll(UUID user) {
        jdbc.update("update user_sessions set revoked=true where user_id=?", user);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
    public void cleanup() {
        jdbc.update("delete from user_sessions where expires_at<now()-interval '7 days'");
        jdbc.update("delete from login_throttles where window_started<now()-interval '2 days'");
        jdbc.update("delete from password_reset_requests where last_requested<now()-interval '2 days'");
    }
}
