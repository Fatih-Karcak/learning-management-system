package com.lms.userservice.service;

import com.lms.userservice.domain.enums.UserStatus;
import com.lms.userservice.event.PlatformNotice;
import com.lms.userservice.repository.UserRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PlatformEvents events;
    private final PasswordEncoder encoder;
    private final SessionService sessions;

    @Transactional
    public void request(String email, boolean verification) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String hash = SessionService.hash(normalized + (verification ? ":verify" : ":reset"));
        int claim = jdbc.update(
            "insert into password_reset_requests(email_hash,last_requested) values(?,now()) on conflict(email_hash) do update set last_requested=now() where password_reset_requests.last_requested<now()-interval '2 minutes'",
            hash
        );
        if (claim == 0) return;
        var found = users.findByEmail(normalized);
        if (found.isEmpty()) return;
        var u = found.get();
        if (u.getStatus() == UserStatus.BANNED || u.getStatus() == UserStatus.INACTIVE) return;
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        if (verification) {
            if (u.isEmailVerified()) return;
            u.setEmailVerificationToken(token);
            u.setVerificationExpiresAt(LocalDateTime.now(java.time.Clock.systemUTC()).plusHours(24));
        } else {
            u.setPasswordResetToken(SessionService.hash(token));
            u.setPasswordResetTokenExpiry(LocalDateTime.now(java.time.Clock.systemUTC()).plusMinutes(30));
        }
        users.save(u);
        events.enqueue(
            new PlatformNotice(
                UUID.randomUUID(),
                u.getId(),
                null,
                verification ? "Verify your email address" : "Reset your password",
                verification ? "This link is valid for 24 hours." : "This link is valid for 30 minutes.",
                verification ? "/verify-email" : "/reset-password",
                "SECURITY",
                token
            )
        );
    }

    @Transactional
    public void reset(String token, String password) {
        var u = users
            .findByPasswordResetToken(SessionService.hash(token))
            .orElseThrow(() -> new IllegalArgumentException("The link is invalid or has already been used"));
        jdbc.queryForList("select id from users where id=? for update", u.getId());
        var current = jdbc.queryForMap(
            "select password_reset_token,password_reset_token_expiry from users where id=?",
            u.getId()
        );
        if (
            !SessionService.hash(token).equals(current.get("password_reset_token")) ||
            current.get("password_reset_token_expiry") == null ||
            ((java.sql.Timestamp) current.get("password_reset_token_expiry"))
                .toLocalDateTime()
                .isBefore(LocalDateTime.now(java.time.Clock.systemUTC()))
        ) throw new IllegalArgumentException("The link has expired or has already been used");
        u.setPassword(encoder.encode(password));
        u.setPasswordResetToken(null);
        u.setPasswordResetTokenExpiry(null);
        users.saveAndFlush(u);
        sessions.revokeAll(u.getId());
    }
}
