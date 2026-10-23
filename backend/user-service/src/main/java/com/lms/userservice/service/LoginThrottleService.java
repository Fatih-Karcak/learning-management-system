package com.lms.userservice.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginThrottleService {

    private final JdbcTemplate jdbc;

    private String hash(String email) {
        return SessionService.hash(email.trim().toLowerCase(Locale.ROOT));
    }

    public void check(String email) {
        if (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from login_throttles where email_hash=? and blocked_until>now())",
                    Boolean.class,
                    hash(email)
                )
            )
        ) throw new BadCredentialsException("Çok fazla giriş denemesi. 10 dakika sonra tekrar deneyin");
    }

    public void failure(String email) {
        jdbc.update(
            "insert into login_throttles(email_hash,failures) values(?,1) on conflict(email_hash) do update set failures=case when login_throttles.window_started<now()-interval '10 minutes' then 1 else login_throttles.failures+1 end,window_started=case when login_throttles.window_started<now()-interval '10 minutes' then now() else login_throttles.window_started end,blocked_until=case when login_throttles.window_started>=now()-interval '10 minutes' and login_throttles.failures>=5 then now()+interval '10 minutes' else null end",
            hash(email)
        );
    }

    public void success(String email) {
        jdbc.update("delete from login_throttles where email_hash=?", hash(email));
    }
}
