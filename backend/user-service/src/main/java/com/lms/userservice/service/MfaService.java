package com.lms.userservice.service;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MfaService {

    private final JdbcTemplate jdbc;

    @Value("${app.mfa.encryption-key:${jwt.secret}}")
    private String encryptionKey;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public record Setup(String secret, String uri) {}

    public boolean enabled(UUID user) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from user_mfa where user_id=? and enabled)",
                Boolean.class,
                user
            )
        );
    }

    @Transactional
    public Setup setup(UUID user, String email) {
        jdbc.queryForList("select id from users where id=? for update", user);
        if (enabled(user)) throw new IllegalStateException("İki aşamalı doğrulama zaten açık");
        byte[] seed = new byte[20];
        new SecureRandom().nextBytes(seed);
        StringBuilder b = new StringBuilder();
        int buffer = 0,
            bits = 0;
        for (byte v : seed) {
            buffer = (buffer << 8) | (v & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                b.append(ALPHABET.charAt((buffer >> bits) & 31));
            }
        }
        String secret = b.toString();
        jdbc.update(
            "insert into user_mfa(user_id,secret) values(?,?) on conflict(user_id) do update set secret=excluded.secret,last_step=-1",
            user,
            encrypt(secret)
        );
        return new Setup(
            secret,
            "otpauth://totp/" +
                java.net.URLEncoder.encode("İleri Akademi:" + email, StandardCharsets.UTF_8) +
                "?secret=" +
                secret +
                "&issuer=" +
                java.net.URLEncoder.encode("İleri Akademi", StandardCharsets.UTF_8) +
                "&algorithm=SHA1&digits=6&period=30"
        );
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void verify(UUID user, String code, boolean activation) {
        var rows = jdbc.queryForList("select * from user_mfa where user_id=? for update", user);
        if (rows.isEmpty()) {
            if (activation) throw new BadCredentialsException("Önce doğrulama kurulumu yapın");
            return;
        }
        var r = rows.getFirst();
        if (!activation && !Boolean.TRUE.equals(r.get("enabled"))) return;
        var until = (java.sql.Timestamp) r.get("locked_until");
        if (
            until != null && until.toInstant().isAfter(java.time.Instant.now())
        ) throw new BadCredentialsException("Çok fazla doğrulama denemesi; daha sonra deneyin");
        long step = System.currentTimeMillis() / 30000,
            last = ((Number) r.get("last_step")).longValue(),
            accepted = -1;
        if (code != null && code.matches("[0-9]{6}")) for (long s = step - 1; s <= step + 1; s++) if (
            s > last &&
            MessageDigest.isEqual(
                totp(decrypt((String) r.get("secret")), s).getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8)
            )
        ) accepted = s;
        if (accepted < 0) {
            jdbc.update(
                "update user_mfa set failed_attempts=failed_attempts+1,locked_until=case when failed_attempts>=4 then now()+interval '5 minutes' else locked_until end where user_id=?",
                user
            );
            throw new BadCredentialsException("Doğrulama kodu gerekli, geçersiz veya kullanılmış");
        }
        jdbc.update(
            "update user_mfa set enabled=true,last_step=?,failed_attempts=0,locked_until=null where user_id=?",
            accepted,
            user
        );
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public void disable(UUID user, String code) {
        verify(user, code, false);
        jdbc.update("delete from user_mfa where user_id=?", user);
    }

    public static String totp(String secret, long step) {
        try {
            byte[] key = new byte[(secret.length() * 5) / 8];
            int buffer = 0,
                bits = 0,
                index = 0;
            for (char c : secret.toCharArray()) {
                buffer = (buffer << 5) | ALPHABET.indexOf(c);
                bits += 5;
                if (bits >= 8) {
                    bits -= 8;
                    key[index++] = (byte) (buffer >> bits);
                }
            }
            Mac m = Mac.getInstance("HmacSHA1");
            m.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = m.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[19] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1000000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        return new SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(
                ("lms-mfa-v1:" + encryptionKey).getBytes(StandardCharsets.UTF_8)
            ),
            "AES"
        );
    }

    private String encrypt(String raw) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] data = c.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(12 + data.length)
                    .put(nonce)
                    .put(data)
                    .array()
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String decrypt(String raw) {
        try {
            byte[] b = Base64.getDecoder().decode(raw);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(b, 12)));
            return new String(c.doFinal(Arrays.copyOfRange(b, 12, b.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
