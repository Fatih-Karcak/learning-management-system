package com.lms.userservice.service;

import com.lms.userservice.domain.enums.*;
import com.lms.userservice.repository.UserRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AccountToolsService {

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SessionService sessions;

    public record Preferences(
        boolean learningEmails,
        boolean announcements,
        @Min(10) @Max(3000) int weeklyTargetMinutes
    ) {}

    public record Ticket(
        @NotBlank @Size(max = 200) String subject,
        @NotNull @Pattern(regexp = "TECHNICAL|COURSE|CONTENT_REPORT|ACCOUNT") String category,
        @NotBlank @Size(max = 10000) String content,
        UUID courseId
    ) {}

    public record Reply(@NotBlank @Size(max = 10000) String content, boolean close) {}

    public Preferences preferences(UUID user) {
        var r = jdbc.queryForList("select * from account_preferences where user_id=?", user);
        return r.isEmpty()
            ? new Preferences(true, true, 60)
            : new Preferences(
                  (Boolean) r.getFirst().get("learning_emails"),
                  (Boolean) r.getFirst().get("announcements"),
                  ((Number) r.getFirst().get("weekly_target_minutes")).intValue()
              );
    }

    public Preferences savePreferences(UUID user, Preferences p) {
        jdbc.update(
            "insert into account_preferences(user_id,learning_emails,announcements,weekly_target_minutes) values(?,?,?,?) on conflict(user_id) do update set learning_emails=excluded.learning_emails,announcements=excluded.announcements,weekly_target_minutes=excluded.weekly_target_minutes",
            user,
            p.learningEmails(),
            p.announcements(),
            p.weeklyTargetMinutes()
        );
        return p;
    }

    public List<Map<String, Object>> tickets(UUID user, boolean admin) {
        return admin
            ? jdbc.queryForList(
                  "select t.*,u.email from support_tickets t join users u on u.id=t.user_id order by (t.status='OPEN') desc,t.updated_at desc limit 100"
              )
            : jdbc.queryForList(
                  "select * from support_tickets where user_id=? order by updated_at desc limit 100",
                  user
              );
    }

    public Map<String, Object> ticket(UUID id, UUID user, boolean admin) {
        var rows = jdbc.queryForList("select * from support_tickets where id=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var t = rows.getFirst();
        if (!admin && !user.equals(t.get("user_id"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return t;
    }

    public Map<String, Object> create(UUID user, Ticket input) {
        var id = UUID.randomUUID();
        jdbc.update(
            "insert into support_tickets(id,user_id,subject,category,content,course_id) values(?,?,?,?,?,?)",
            id,
            user,
            input.subject(),
            input.category(),
            input.content(),
            input.courseId()
        );
        return ticket(id, user, false);
    }

    public List<Map<String, Object>> replies(UUID id, UUID user, boolean admin) {
        ticket(id, user, admin);
        return jdbc.queryForList(
            "select id,staff,content,created_at from support_replies where ticket_id=? order by created_at limit 300",
            id
        );
    }

    @Transactional
    public void reply(UUID id, UUID user, boolean admin, Reply input) {
        ticket(id, user, admin);
        jdbc.update(
            "insert into support_replies(id,ticket_id,author_id,staff,content) values(?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            user,
            admin,
            input.content()
        );
        jdbc.update(
            "update support_tickets set status=?,updated_at=now() where id=?",
            admin && input.close() ? "CLOSED" : "OPEN",
            id
        );
    }

    public Map<String, Object> export(UUID user) {
        var profile = jdbc.queryForMap(
            "select id,email,first_name,last_name,bio,phone_number,role,status,created_at from users where id=?",
            user
        );
        return Map.of(
            "profile",
            profile,
            "preferences",
            preferences(user),
            "tickets",
            tickets(user, false),
            "privacyRequests",
            privacyRequests(user, false),
            "sessions",
            sessions.list(user)
        );
    }

    public List<Map<String, Object>> privacyRequests(UUID user, boolean admin) {
        return admin
            ? jdbc.queryForList(
                  "select p.*,u.email from privacy_requests p join users u on u.id=p.user_id order by p.created_at desc limit 100"
              )
            : jdbc.queryForList(
                  "select * from privacy_requests where user_id=? order by created_at desc",
                  user
              );
    }

    @Transactional
    public void requestErasure(UUID user, String password) {
        var u = users.findById(user).orElseThrow();
        if (u.getRole() == UserRole.ADMIN) throw new IllegalStateException(
            "Önce yönetici rolünüzü başka bir yönetici değiştirmeli"
        );
        if (!passwords.matches(password, u.getPassword())) throw new IllegalArgumentException(
            "Parola yanlış"
        );
        jdbc.queryForList("select id from users where id=? for update", user);
        if (
            !jdbc
                .queryForList(
                    "select id from privacy_requests where user_id=? and kind='ERASURE' and status='OPEN'",
                    user
                )
                .isEmpty()
        ) return;
        jdbc.update(
            "insert into privacy_requests(id,user_id,kind) values(?,?,'ERASURE')",
            UUID.randomUUID(),
            user
        );
        u.setStatus(UserStatus.INACTIVE);
        users.save(u);
        sessions.revokeAll(user);
    }

    @Transactional
    public void resolvePrivacy(UUID id, UUID actor, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new IllegalArgumentException(
            "Gerekçe gerekli"
        );
        var r = jdbc.queryForMap("select * from privacy_requests where id=? for update", id);
        if (!"OPEN".equals(r.get("status"))) throw new IllegalStateException("Talep zaten işlendi");
        jdbc.update(
            "update privacy_requests set status='REVIEWED',reason=?,resolved_at=now() where id=?",
            reason,
            id
        );
        jdbc.update(
            "insert into admin_audit(id,actor_id,target_id,action,reason) values(?,?,?,'PRIVACY_REVIEW',?)",
            UUID.randomUUID(),
            actor,
            r.get("user_id"),
            reason
        );
    }
}
