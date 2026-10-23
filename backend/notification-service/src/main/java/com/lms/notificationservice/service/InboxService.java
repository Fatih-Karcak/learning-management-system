package com.lms.notificationservice.service;

import com.lms.notificationservice.client.UserClient;
import com.lms.notificationservice.event.PlatformNotice;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserClient users;
    private final EmailService mail;

    @Value("${app.frontend-base-url:${app.public-base-url}}")
    private String frontend;

    @KafkaListener(
        topics = "platform-notices",
        groupId = "notification-service",
        containerFactory = "platformNoticeFactory"
    )
    @Transactional
    public void receive(PlatformNotice notice) {
        if (notice.id() == null || notice.recipientId() == null) throw new IllegalArgumentException(
            "Bildirim alıcısı eksik"
        );
        int created = jdbc.update(
            "insert into notification_deliveries(id,recipient_id,payload) values(?,?,?) on conflict do nothing",
            notice.id(),
            notice.recipientId(),
            json.writeValueAsString(notice)
        );
        if (created == 0) return;
        if (!"SECURITY".equals(notice.kind())) jdbc.update(
            "insert into notification_inbox(id,user_id,subject,message,path,kind) values(?,?,?,?,?,?) on conflict do nothing",
            notice.id(),
            notice.recipientId(),
            notice.subject(),
            notice.message(),
            notice.path(),
            notice.kind()
        );
    }

    @Scheduled(fixedDelayString = "${notification.delivery-delay:5000}")
    @Transactional
    public void deliver() {
        for (var row : jdbc.queryForList(
            "select * from notification_deliveries where status in('PENDING','RETRY') and next_attempt_at<=now() order by created_at limit 20 for update skip locked"
        )) {
            try {
                var e = json.readValue((String) row.get("payload"), PlatformNotice.class);
                var result = users.getUserById(e.recipientId());
                if (result == null || result.data() == null) throw new IllegalStateException(
                    "Kullanıcıya ulaşılamadı"
                );
                if (!"SECURITY".equals(e.kind())) {
                    var prefs = users.preferences(e.recipientId());
                    if (prefs == null || prefs.data() == null) throw new IllegalStateException(
                        "Bildirim tercihlerine ulaşılamadı"
                    );
                    if (
                        !prefs.data().learningEmails() ||
                        ("ANNOUNCEMENT".equals(e.kind()) && !prefs.data().announcements())
                    ) {
                        jdbc.update(
                            "update notification_deliveries set status='SKIPPED',payload='{}' where id=?",
                            e.id()
                        );
                        continue;
                    }
                }
                String path = e.path() == null ? "/notifications" : e.path();
                if (!path.startsWith("/") || path.startsWith("//")) path = "/notifications";
                String url = frontend.replaceAll("/+$", "") + path;
                if (e.token() != null) url +=
                    "?token=" +
                    java.net.URLEncoder.encode(e.token(), java.nio.charset.StandardCharsets.UTF_8);
                var ctx = new Context();
                ctx.setVariable("subject", e.subject());
                ctx.setVariable("message", e.message());
                ctx.setVariable("url", url);
                mail.sendHtmlEmail(result.data().email(), e.subject(), "platform-notice", ctx);
                jdbc.update(
                    "update notification_deliveries set status='SENT',payload='{}',last_error=null,attempts=attempts+1 where id=?",
                    e.id()
                );
            } catch (Exception e) {
                jdbc.update(
                    "update notification_deliveries set status=case when attempts>=4 then 'FAILED' else 'RETRY' end,attempts=attempts+1,next_attempt_at=now()+interval '2 minutes',last_error=? where id=?",
                    e.getClass().getSimpleName(),
                    row.get("id")
                );
            }
        }
        jdbc.update(
            "delete from notification_deliveries where status in('SENT','SKIPPED') and created_at<now()-interval '30 days'"
        );
    }

    public List<Map<String, Object>> inbox(UUID user) {
        return jdbc.queryForList(
            "select * from notification_inbox where user_id=? order by created_at desc limit 200",
            user
        );
    }

    public void read(UUID user, UUID id) {
        jdbc.update(
            "update notification_inbox set read_at=coalesce(read_at,now()) where id=? and user_id=?",
            id,
            user
        );
    }

    public List<Map<String, Object>> failures() {
        return jdbc.queryForList(
            "select id,recipient_id,status,attempts,last_error,created_at from notification_deliveries where status in('FAILED','RETRY') order by created_at desc limit 100"
        );
    }

    public void retry(UUID id) {
        jdbc.update(
            "update notification_deliveries set status='RETRY',attempts=0,next_attempt_at=now() where id=? and status='FAILED'",
            id
        );
    }
}
