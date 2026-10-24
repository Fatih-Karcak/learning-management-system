package com.lms.enrollmentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LearningReminderTest {
  @Test
  void repeatedSchedulerRunsCreateOnlyOneDailyNotice() {
    var jdbc = mock(JdbcTemplate.class);
    var events = mock(OutboxService.class);
    UUID user = UUID.randomUUID();
    when(jdbc.queryForList(contains("for update skip locked")))
        .thenReturn(List.of(Map.of("student_id", user, "time_zone", "UTC", "local_hour", 18)));
    when(jdbc.queryForObject(
            contains("learning_tasks"), eq(Integer.class), eq(user), any(LocalDate.class)))
        .thenReturn(2);
    when(jdbc.queryForObject(contains("enrollments"), eq(Integer.class), eq(user))).thenReturn(1);
    when(jdbc.update(
            startsWith("insert into learning_reminder_deliveries"),
            eq(user),
            any(LocalDate.class),
            any(UUID.class)))
        .thenReturn(1, 0);
    var service = new LearningReminderService(jdbc, events);
    service.deliver();
    service.deliver();
    verify(events, times(1)).enqueue(eq("platform-notices"), anyString(), any());
  }

  @Test
  void schedulesInTheLearnersTimeZone() {
    assertEquals(
        Instant.parse("2026-10-24T15:00:00Z"),
        LearningReminderService.nextRun(
            "Europe/Istanbul", 18, Instant.parse("2026-10-24T14:00:00Z")));
    assertEquals(
        Instant.parse("2026-10-25T15:00:00Z"),
        LearningReminderService.nextRun(
            "Europe/Istanbul", 18, Instant.parse("2026-10-24T15:00:00Z")));
  }

  @Test
  void daylightSavingGapsStillProduceAFutureRun() {
    var now = Instant.parse("2026-03-29T00:00:00Z");
    var next = LearningReminderService.nextRun("Europe/Berlin", 2, now);
    assertTrue(next.isAfter(now));
    assertEquals(Instant.parse("2026-03-29T01:00:00Z"), next);
  }

  @Test
  void invalidTimeZonesAreRejected() {
    assertThrows(
        DateTimeException.class,
        () -> LearningReminderService.nextRun("invalid/timezone", 18, Instant.now()));
  }
}
