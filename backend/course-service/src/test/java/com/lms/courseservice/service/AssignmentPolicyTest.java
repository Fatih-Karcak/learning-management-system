package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AssignmentPolicyTest {
  @Test
  void derivesThePercentageFromRubricPoints() {
    UUID accuracy = UUID.randomUUID(), clarity = UUID.randomUUID();
    var rubric =
        List.of(
            new AssignmentPolicyService.Criterion(accuracy, "Accuracy", 80),
            new AssignmentPolicyService.Criterion(clarity, "Clarity", 20));
    assertEquals(75, AssignmentPolicyService.score(rubric, Map.of(accuracy, 60, clarity, 15)));
    assertThrows(
        IllegalArgumentException.class,
        () -> AssignmentPolicyService.score(rubric, Map.of(accuracy, 81, clarity, 15)));
    assertThrows(
        IllegalArgumentException.class,
        () -> AssignmentPolicyService.score(rubric, Map.of(accuracy, 60)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AssignmentPolicyService.score(
                rubric, Map.of(accuracy, 60, clarity, 15, UUID.randomUUID(), 100)));
  }

  @Test
  void validatesSingleAndMultipleAnswerKeys() {
    var multi =
        new AssessmentService.Question(
            UUID.randomUUID(),
            "Choose both",
            List.of("A", "B", "C"),
            0,
            "Explanation",
            "MULTIPLE_CHOICE",
            Set.of(0, 2),
            3);
    assertDoesNotThrow(() -> AssessmentService.validateQuestion(multi));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AssessmentService.validateQuestion(
                new AssessmentService.Question(
                    UUID.randomUUID(),
                    "Invalid",
                    List.of("A", "B"),
                    0,
                    "",
                    "SINGLE_CHOICE",
                    Set.of(0, 1),
                    1)));
  }

  @Test
  void acceptsTheNewAnswerFormatWithoutALegacySingleOption() {
    UUID id = UUID.randomUUID();
    var answer =
        new tools.jackson.databind.ObjectMapper()
            .readValue(
                "{\"questionId\":\"" + id + "\",\"options\":[0,2]}",
                AssessmentService.Answer.class);
    assertEquals(List.of(0, 2), answer.options());
    assertNull(answer.option());
  }

  @Test
  void deadlinesRejectClosedSubmissionsAndMarkAllowedLateWork() {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var json = new tools.jackson.databind.ObjectMapper();
    var service = new AssignmentPolicyService(jdbc, json);
    UUID lesson = UUID.randomUUID();
    var past = java.time.Instant.now().minusSeconds(60);
    org.mockito.Mockito.when(
            jdbc.queryForList(
                "select document,revision from assignment_policies where lesson_id=?", lesson))
        .thenReturn(
            List.of(
                Map.of(
                    "document",
                    json.writeValueAsString(
                        new AssignmentPolicyService.Policy(1, past, false, List.of())),
                    "revision",
                    1L)));
    assertEquals(
        409,
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.requireSubmission(lesson))
            .getStatusCode()
            .value());
    org.mockito.Mockito.when(
            jdbc.queryForList(
                "select document,revision from assignment_policies where lesson_id=?", lesson))
        .thenReturn(
            List.of(
                Map.of(
                    "document",
                    json.writeValueAsString(
                        new AssignmentPolicyService.Policy(2, past, true, List.of())),
                    "revision",
                    2L)));
    assertTrue(service.requireSubmission(lesson));
  }
}
