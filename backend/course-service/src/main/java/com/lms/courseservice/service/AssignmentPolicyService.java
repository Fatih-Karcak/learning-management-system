package com.lms.courseservice.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AssignmentPolicyService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public record Criterion(
      @NotNull UUID id, @NotBlank @Size(max = 200) String label, @Min(1) @Max(100) int maxPoints) {}

  public record Policy(
      @Min(0) long revision,
      Instant dueAt,
      boolean allowLate,
      @NotNull @Size(max = 20) List<@Valid Criterion> rubric) {}

  public Policy get(UUID lesson) {
    var rows =
        jdbc.queryForList(
            "select document,revision from assignment_policies where lesson_id=?", lesson);
    if (rows.isEmpty()) return new Policy(0, null, true, List.of());
    var policy = json.readValue((String) rows.getFirst().get("document"), Policy.class);
    return new Policy(
        ((Number) rows.getFirst().get("revision")).longValue(),
        policy.dueAt(),
        policy.allowLate(),
        policy.rubric());
  }

  @Transactional
  public Policy save(UUID lesson, Policy input) {
    jdbc.queryForList("select id from lessons where id=? for update", lesson);
    if (input.revision() != get(lesson).revision())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Assignment policy changed; reload before saving");
    if (input.rubric().stream().map(Criterion::id).distinct().count() != input.rubric().size())
      throw new IllegalArgumentException("Duplicate rubric criteria");
    var policy = new Policy(input.revision() + 1, input.dueAt(), input.allowLate(), input.rubric());
    jdbc.update(
        "insert into assignment_policies(lesson_id,document,revision) values(?,?,?) on"
            + " conflict(lesson_id) do update set"
            + " document=excluded.document,revision=excluded.revision,updated_at=now()",
        lesson,
        json.writeValueAsString(policy),
        policy.revision());
    return policy;
  }

  public boolean requireSubmission(UUID lesson) {
    return requireSubmission(get(lesson));
  }

  public static boolean requireSubmission(Policy policy) {
    boolean late = policy.dueAt() != null && Instant.now().isAfter(policy.dueAt());
    if (late && !policy.allowLate())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "The assignment deadline has passed");
    return late;
  }

  public static int score(List<Criterion> rubric, Map<UUID, Integer> scores) {
    if (!scores
        .keySet()
        .equals(rubric.stream().map(Criterion::id).collect(java.util.stream.Collectors.toSet())))
      throw new IllegalArgumentException("Score every rubric criterion exactly once");
    int earned = 0, total = 0;
    for (var criterion : rubric) {
      Integer value = scores.get(criterion.id());
      if (value == null || value < 0 || value > criterion.maxPoints())
        throw new IllegalArgumentException("Rubric score is outside its allowed range");
      earned += value;
      total += criterion.maxPoints();
    }
    if (total == 0) throw new IllegalArgumentException("A rubric must contain criteria");
    return (int) Math.round(100.0 * earned / total);
  }
}
