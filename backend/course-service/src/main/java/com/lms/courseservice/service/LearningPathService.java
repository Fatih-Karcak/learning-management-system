package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LearningPathService {
  private final JdbcTemplate jdbc;
  private final CourseRepository courses;
  private final EnrollmentClient enrollments;

  public record Input(
      @Min(0) long revision,
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 4000) String description,
      boolean published,
      @NotNull @Size(min = 1, max = 50) List<UUID> courses) {}

  public Map<String, Object> list(AuthenticatedUser user, int page) {
    if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
    UUID id = user == null ? null : user.id();
    return Map.of(
        "content",
        jdbc.queryForList(
            "select p.*, (select count(*) from learning_path_steps s where s.path_id=p.id) as"
                + " course_count from learning_paths p where published or owner_id=? order by"
                + " updated_at desc limit 12 offset ?",
            id,
            page * 12),
        "total",
        jdbc.queryForObject(
            "select count(*) from learning_paths where published or owner_id=?", Long.class, id),
        "page",
        page);
  }

  public Map<String, Object> detail(UUID id, AuthenticatedUser user) {
    var rows = jdbc.queryForList("select * from learning_paths where id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var path = rows.getFirst();
    if (!Boolean.TRUE.equals(path.get("published"))
        && (user == null || !user.isAdmin() && !user.id().equals(path.get("owner_id"))))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var steps =
        jdbc.queryForList(
            "select s.course_id,c.title,c.thumbnail_url,c.status,c.instructor_name,s.position from"
                + " learning_path_steps s join courses c on c.id=s.course_id where s.path_id=?"
                + " order by s.position",
            id);
    for (var step : steps) {
      boolean complete = false, enrolled = false;
      if (user != null) {
        var response = enrollments.learning((UUID) step.get("course_id"), user.id());
        if (response == null || !response.success() || response.data() == null)
          throw new ResponseStatusException(
              HttpStatus.SERVICE_UNAVAILABLE, "Progress could not be verified");
        complete = response.data().completed();
        enrolled = response.data().enrolled();
      }
      step.put("completed", complete);
      step.put("enrolled", enrolled);
    }
    return Map.of("path", path, "steps", steps);
  }

  @Transactional
  public Map<String, Object> save(UUID id, Input input, AuthenticatedUser user) {
    if (!user.isAdmin() && !"INSTRUCTOR".equals(user.role()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (new HashSet<>(input.courses()).size() != input.courses().size())
      throw new IllegalArgumentException("A course can appear only once");
    for (UUID course : input.courses()) {
      var value =
          courses
              .findById(course)
              .orElseThrow(() -> new IllegalArgumentException("Course not found"));
      if (input.published() && value.getStatus() != CourseStatus.PUBLISHED)
        throw new IllegalArgumentException("Publish all courses before publishing a path");
      if (!input.published()
          && !user.isAdmin()
          && value.getStatus() != CourseStatus.PUBLISHED
          && !value.getInstructorId().equals(user.id()))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is private");
    }
    if (id == null) {
      id = UUID.randomUUID();
      jdbc.update(
          "insert into learning_paths(id,owner_id,title,description,published) values(?,?,?,?,?)",
          id,
          user.id(),
          input.title(),
          input.description(),
          input.published());
    } else {
      var rows =
          jdbc.queryForList(
              "select owner_id,revision from learning_paths where id=? for update", id);
      if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      if (!user.isAdmin() && !user.id().equals(rows.getFirst().get("owner_id")))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      if (input.revision() != ((Number) rows.getFirst().get("revision")).longValue())
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Path changed; reload before saving");
      jdbc.update(
          "update learning_paths set"
              + " title=?,description=?,published=?,revision=revision+1,updated_at=now() where"
              + " id=?",
          input.title(),
          input.description(),
          input.published(),
          id);
      jdbc.update("delete from learning_path_steps where path_id=?", id);
    }
    for (int i = 0; i < input.courses().size(); i++)
      jdbc.update(
          "insert into learning_path_steps(path_id,course_id,position) values(?,?,?)",
          id,
          input.courses().get(i),
          i);
    return detail(id, user);
  }
}
