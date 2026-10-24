package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LearningPolicyService {
  private final JdbcTemplate jdbc;
  private final CourseRepository courses;
  private final EnrollmentClient enrollments;

  public record Rules(
      @Min(0) long revision,
      boolean sequential,
      @Min(0) @Max(365) int releaseDays,
      @NotNull @Size(max = 20) List<UUID> prerequisites) {}

  private Course owner(UUID id, AuthenticatedUser user) {
    var course =
        courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!user.isAdmin() && !course.getInstructorId().equals(user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return course;
  }

  public Rules rules(UUID id, AuthenticatedUser user) {
    owner(id, user);
    return rules(id);
  }

  private Rules rules(UUID id) {
    var rows = jdbc.queryForList("select * from course_learning_rules where course_id=?", id);
    var prerequisites =
        jdbc.queryForList(
            "select prerequisite_id from course_prerequisites where course_id=? order by"
                + " prerequisite_id",
            UUID.class,
            id);
    return rows.isEmpty()
        ? new Rules(0, false, 0, prerequisites)
        : new Rules(
            ((Number) rows.getFirst().get("revision")).longValue(),
            (Boolean) rows.getFirst().get("sequential"),
            ((Number) rows.getFirst().get("release_days")).intValue(),
            prerequisites);
  }

  @Transactional
  public Rules save(UUID id, Rules input, AuthenticatedUser user) {
    owner(id, user);
    jdbc.queryForList(
        "select pg_advisory_xact_lock(731224101)"); // Serialize graph changes so concurrent edits
    // cannot create a cycle.
    jdbc.queryForList("select id from courses where id=? for update", id);
    if (input.revision() != rules(id).revision())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Learning rules changed; reload before saving");
    if (new HashSet<>(input.prerequisites()).size() != input.prerequisites().size())
      throw new IllegalArgumentException("Duplicate prerequisites");
    for (UUID required : input.prerequisites()) {
      var course =
          courses
              .findById(required)
              .orElseThrow(() -> new IllegalArgumentException("Prerequisite course not found"));
      if (course.getStatus() != CourseStatus.PUBLISHED)
        throw new IllegalArgumentException("Prerequisites must be published courses");
      boolean cycle =
          required.equals(id)
              || Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "with recursive dependencies(id) as (select prerequisite_id from"
                          + " course_prerequisites where course_id=? union select p.prerequisite_id"
                          + " from course_prerequisites p join dependencies d on p.course_id=d.id)"
                          + " select exists(select 1 from dependencies where id=?)",
                      Boolean.class,
                      required,
                      id));
      if (cycle) throw new IllegalArgumentException("Course prerequisites cannot form a cycle");
    }
    jdbc.update(
        "insert into course_learning_rules(course_id,revision,sequential,release_days)"
            + " values(?,?,?,?) on conflict(course_id) do update set"
            + " revision=excluded.revision,sequential=excluded.sequential,release_days=excluded.release_days,effective_at=now()",
        id,
        input.revision() + 1,
        input.sequential(),
        input.releaseDays());
    jdbc.update("delete from course_prerequisites where course_id=?", id);
    for (UUID required : input.prerequisites())
      jdbc.update(
          "insert into course_prerequisites(course_id,prerequisite_id) values(?,?)", id, required);
    jdbc.update(
        "insert into course_review_events(id,course_id,actor_id,decision,reason)"
            + " values(?,?,?,'LEARNING_RULES','Learning access rules updated')",
        UUID.randomUUID(),
        id,
        user.id());
    return rules(id);
  }

  private EnrollmentClient.Snapshot snapshot(UUID course, UUID user) {
    try {
      var response = enrollments.learning(course, user);
      if (response == null || !response.success() || response.data() == null)
        throw new IllegalStateException();
      return response.data();
    } catch (RuntimeException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Learning access could not be verified", ex);
    }
  }

  public String lockReason(Course course, UUID lesson, UUID student) {
    if (course.getSections().stream()
        .flatMap(section -> section.getLessons().stream())
        .anyMatch(item -> item.getId().equals(lesson) && item.isPreviewAllowed())) return null;
    return lockReasons(course, student).get(lesson);
  }

  public Map<UUID, String> lockReasons(Course course, UUID student) {
    var lessons =
        course.getSections().stream().flatMap(section -> section.getLessons().stream()).toList();
    var configured =
        jdbc.queryForList(
            "select sequential,release_days,effective_at from course_learning_rules where"
                + " course_id=?",
            course.getId());
    if (configured.isEmpty()) return Map.of();
    // Load progress once per course response, rather than once for every lesson.
    var state = snapshot(course.getId(), student);
    Instant effective =
        ((java.sql.Timestamp) configured.getFirst().get("effective_at"))
            .toLocalDateTime()
            .toInstant(ZoneOffset.UTC);
    if (state.completed() || (state.enrolledAt() != null && state.enrolledAt().isBefore(effective)))
      return Map.of();
    String prerequisiteReason = null;
    if (state.enrolled())
      for (UUID required : rules(course.getId()).prerequisites()) {
        if (!snapshot(required, student).completed()) {
          prerequisiteReason = "Complete the prerequisite courses first";
          break;
        }
      }
    var result = new HashMap<UUID, String>();
    int days = ((Number) configured.getFirst().get("release_days")).intValue();
    boolean sequential = Boolean.TRUE.equals(configured.getFirst().get("sequential"));
    boolean previousComplete = true;
    Instant now = Instant.now();
    for (int index = 0; index < lessons.size(); index++) {
      var lesson = lessons.get(index);
      String reason = null;
      if (!lesson.isPreviewAllowed() && !state.completedLessons().contains(lesson.getId())) {
        if (!state.enrolled()) reason = "Enroll in this course to continue";
        else if (prerequisiteReason != null) reason = prerequisiteReason;
        else if (days > 0
            && state.enrolledAt() != null
            && now.isBefore(state.enrolledAt().plus(Duration.ofDays((long) days * index))))
          reason = "This lesson has not been released yet";
        else if (sequential && !previousComplete) reason = "Complete the previous lessons first";
      }
      if (reason != null) result.put(lesson.getId(), reason);
      previousComplete &= state.completedLessons().contains(lesson.getId());
    }
    return result;
  }

  public boolean canAccess(UUID courseId, UUID lesson, UUID student) {
    var course =
        courses
            .findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return course.getSections().stream()
            .flatMap(section -> section.getLessons().stream())
            .anyMatch(item -> item.getId().equals(lesson))
        && lockReason(course, lesson, student) == null;
  }

  public boolean canReadResource(UUID courseId, UUID resource, UUID student) {
    var course =
        courses
            .findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var ids =
        jdbc.queryForList(
            "select m.lesson_id from lesson_materials m join lessons l on l.id=m.lesson_id join"
                + " sections s on s.id=l.section_id where s.course_id=? and m.document like ?",
            UUID.class,
            courseId,
            "%/api/v1/media/resources/" + resource + "%");
    var locks = lockReasons(course, student);
    return ids.stream().anyMatch(id -> !locks.containsKey(id));
  }
}
