package com.lms.userservice.service;

import com.lms.userservice.event.PlatformNotice;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PlatformEvents {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final KafkaTemplate<String, Object> kafka;

    public void enqueue(PlatformNotice e) {
        jdbc.update(
            "insert into platform_outbox(id,topic,payload) values(?,?,?) on conflict do nothing",
            e.id(),
            e.recipientId() == null ? "course-notices" : "platform-notices",
            json.writeValueAsString(e)
        );
    }

    @Scheduled(fixedDelayString = "${platform.outbox-delay:5000}")
    @Transactional
    public void deliver() {
        var rows = jdbc.queryForList(
            "select * from platform_outbox where delivered_at is null and next_attempt_at<=now() order by created_at limit 20 for update skip locked"
        );
        for (var row : rows)
            try {
                kafka
                    .send(
                        (String) row.get("topic"),
                        row.get("id").toString(),
                        json.readTree((String) row.get("payload"))
                    )
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
                jdbc.update(
                    "update platform_outbox set delivered_at=now(),payload='{}',last_error=null where id=?",
                    row.get("id")
                );
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                jdbc.update(
                    "update platform_outbox set attempts=attempts+1,next_attempt_at=now()+interval '1 minute',last_error=? where id=?",
                    e.getClass().getSimpleName(),
                    row.get("id")
                );
            }
        jdbc.update("delete from platform_outbox where delivered_at<now()-interval '7 days'");
    }
}
