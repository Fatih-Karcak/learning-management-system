package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.repository.CourseRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class LearningPolicyTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final EnrollmentClient enrollments = mock(EnrollmentClient.class);
  final LearningPolicyService service =
      new LearningPolicyService(jdbc, mock(CourseRepository.class), enrollments);
  final UUID user = UUID.randomUUID(),
      first = UUID.randomUUID(),
      second = UUID.randomUUID(),
      id = UUID.randomUUID();
  final Course course =
      Course.builder()
          .id(id)
          .sections(
              List.of(
                  Section.builder()
                      .lessons(
                          List.of(
                              Lesson.builder().id(first).build(),
                              Lesson.builder().id(second).build()))
                      .build()))
          .build();

  void rules(boolean sequential, int days, Instant enrolled, Set<UUID> completed) {
    when(jdbc.queryForList(
            "select sequential,release_days,effective_at from course_learning_rules where"
                + " course_id=?",
            id))
        .thenReturn(
            List.of(
                Map.of(
                    "sequential",
                    sequential,
                    "release_days",
                    days,
                    "effective_at",
                    Timestamp.from(enrolled.minusSeconds(1)))));
    when(enrollments.learning(id, user))
        .thenReturn(
            new EnrollmentClient.SnapshotResult(
                true, new EnrollmentClient.Snapshot(true, false, enrolled, completed)));
  }

  @Test
  void sequentialRulesAreEnforcedAndCompletedLessonsStayAccessible() {
    rules(true, 0, Instant.now(), Set.of());
    assertNull(service.lockReason(course, first, user));
    assertNotNull(service.lockReason(course, second, user));
    rules(true, 0, Instant.now(), Set.of(first));
    assertNull(service.lockReason(course, second, user));
  }

  @Test
  void releaseDatesCannotBeBypassedByCompletingEarlierLessons() {
    rules(true, 2, Instant.now(), Set.of(first));
    assertNotNull(service.lockReason(course, second, user));
    rules(true, 2, Instant.now().minus(Duration.ofDays(3)), Set.of(first));
    assertNull(service.lockReason(course, second, user));
  }

  @Test
  void accessFailsClosedWhenProgressCannotBeVerified() {
    rules(true, 0, Instant.now(), Set.of());
    when(enrollments.learning(id, user)).thenReturn(null);
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, () -> service.lockReason(course, second, user))
            .getStatusCode()
            .value());
  }

  @Test
  void previouslyGrantedAccessIsPreservedWhenRulesChange() {
    var enrolled = Instant.now().minusSeconds(60);
    rules(true, 1, enrolled, Set.of());
    when(jdbc.queryForList(
            "select sequential,release_days,effective_at from course_learning_rules where"
                + " course_id=?",
            id))
        .thenReturn(
            List.of(
                Map.of(
                    "sequential",
                    true,
                    "release_days",
                    1,
                    "effective_at",
                    Timestamp.from(Instant.now()))));
    assertNull(service.lockReason(course, second, user));
  }

  @Test
  void aWholeCurriculumLoadsEnrollmentProgressOnlyOnce() {
    rules(true, 0, Instant.now(), Set.of());
    var locks = service.lockReasons(course, user);
    assertNull(locks.get(first));
    assertNotNull(locks.get(second));
    verify(enrollments, times(1)).learning(id, user);
  }
}
