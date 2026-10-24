package com.lms.enrollmentservice.service;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningReminderService {
  private final JdbcTemplate jdbc;
  private final OutboxService events;

  public record Preferences(
      boolean enabled, @NotBlank @Size(max = 100) String timeZone, @Min(0) @Max(23) int hour) {}

  public record Notice(
      UUID id,
      UUID recipientId,
      UUID courseId,
      String subject,
      String message,
      String path,
      String kind,
      String token) {}

  public Preferences get(UUID user) {
    var rows =
        jdbc.queryForList(
            "select enabled,time_zone,local_hour from learning_reminder_preferences where"
                + " student_id=?",
            user);
    return rows.isEmpty()
        ? new Preferences(false, "UTC", 18)
        : new Preferences(
            (Boolean) rows.getFirst().get("enabled"),
            (String) rows.getFirst().get("time_zone"),
            ((Number) rows.getFirst().get("local_hour")).intValue());
  }

  public static Instant nextRun(String zone, int hour, Instant now) {
    var local = now.atZone(ZoneId.of(zone));
    var next = local.toLocalDate().atTime(hour, 0).atZone(local.getZone());
    if (!next.toInstant().isAfter(now))
      next = local.toLocalDate().plusDays(1).atTime(hour, 0).atZone(local.getZone());
    return next.toInstant();
  }

  public Preferences save(UUID user, Preferences input) {
    Instant next;
    try {
      next = nextRun(input.timeZone(), input.hour(), Instant.now());
    } catch (DateTimeException ex) {
      throw new IllegalArgumentException("Choose a valid IANA time zone");
    }
    jdbc.update(
        "insert into"
            + " learning_reminder_preferences(student_id,enabled,time_zone,local_hour,next_run_at)"
            + " values(?,?,?,?,?) on conflict(student_id) do update set"
            + " enabled=excluded.enabled,time_zone=excluded.time_zone,local_hour=excluded.local_hour,next_run_at=excluded.next_run_at,updated_at=now()",
        user,
        input.enabled(),
        input.timeZone(),
        input.hour(),
        LocalDateTime.ofInstant(next, ZoneOffset.UTC));
    return input;
  }

  @Scheduled(fixedDelayString = "${learning.reminders.interval-ms:60000}")
  @Transactional
  public void deliver() {
    Instant now = Instant.now();
    for (var row :
        jdbc.queryForList(
            "select * from learning_reminder_preferences where enabled and next_run_at<=now() order"
                + " by next_run_at limit 100 for update skip locked")) {
      UUID user = (UUID) row.get("student_id");
      String zone = (String) row.get("time_zone");
      int hour = ((Number) row.get("local_hour")).intValue();
      LocalDate date = now.atZone(ZoneId.of(zone)).toLocalDate();
      int due =
          jdbc.queryForObject(
              "select count(*) from learning_tasks where student_id=? and not done and due_date<=?",
              Integer.class,
              user,
              date.plusDays(1));
      int inactive =
          jdbc.queryForObject(
              "select count(*) from enrollments where student_id=? and status='ACTIVE' and"
                  + " (last_accessed_at is null or last_accessed_at<now()-interval '7 days')",
              Integer.class,
              user);
      if (due > 0 || inactive > 0) {
        UUID event =
            UUID.nameUUIDFromBytes(
                ("study-reminder:" + user + ":" + date)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int created =
            jdbc.update(
                "insert into learning_reminder_deliveries(student_id,local_date,event_id)"
                    + " values(?,?,?) on conflict do nothing",
                user,
                date,
                event);
        if (created == 1)
          events.enqueue(
              "platform-notices",
              event.toString(),
              new Notice(
                  event,
                  user,
                  null,
                  "Your learning reminder",
                  "You have "
                      + due
                      + " upcoming or overdue study tasks and "
                      + inactive
                      + " courses waiting for your next session.",
                  "/study-plan",
                  "LEARNING_REMINDER",
                  null));
      }
      jdbc.update(
          "update learning_reminder_preferences set next_run_at=? where student_id=?",
          LocalDateTime.ofInstant(nextRun(zone, hour, now), ZoneOffset.UTC),
          user);
    }
    jdbc.update(
        "delete from learning_reminder_deliveries where created_at<now()-interval '90 days'");
  }
}
