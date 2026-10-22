package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.domain.enums.LessonType;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.repository.LessonRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class AssessmentTest {

    final LessonRepository lessons = mock(LessonRepository.class);
    final CourseAccessPolicy access = mock(CourseAccessPolicy.class);
    final EnrollmentClient enrollments = mock(EnrollmentClient.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ObjectMapper json = new ObjectMapper();
    final AssessmentService service = new AssessmentService(lessons, access, enrollments, jdbc, json);
    final UUID courseId = UUID.randomUUID(),
        lessonId = UUID.randomUUID(),
        questionId = UUID.randomUUID(),
        enrollmentId = UUID.randomUUID();
    final AuthenticatedUser student = new AuthenticatedUser(UUID.randomUUID(), "Student", "STUDENT");
    final Course course = Course.builder().id(courseId).instructorId(UUID.randomUUID()).build();
    final Section section = Section.builder().id(UUID.randomUUID()).course(course).build();
    final Lesson lesson = Lesson.builder().id(lessonId).section(section).type(LessonType.QUIZ).build();

    AssessmentTest() {
        when(lessons.findById(lessonId)).thenReturn(Optional.of(lesson));
        var quiz = new AssessmentService.Quiz(
            70,
            List.of(
                new AssessmentService.Question(
                    questionId,
                    "Question",
                    List.of("Right", "Wrong"),
                    0,
                    "Private explanation"
                )
            )
        );
        when(jdbc.queryForList(anyString(), eq(String.class), eq(lessonId))).thenReturn(
            List.of(json.writeValueAsString(quiz))
        );
        when(enrollments.verify(enrollmentId, courseId, student.id())).thenReturn(
            new EnrollmentClient.Result(true, true)
        );
        when(enrollments.complete(enrollmentId, lessonId, section.getId(), student.id())).thenReturn(
            new EnrollmentClient.Completion(true, "ok")
        );
    }

    @Test
    void studentPayloadNeverContainsAnswerKeyOrPrivateExplanation() {
        String payload = json.writeValueAsString(service.view(courseId, lessonId, student));
        assertFalse(payload.contains("correctOption"));
        assertFalse(payload.contains("explanation"));
        assertFalse(payload.contains("Private"));
    }

    @Test
    void passingAttemptUsesServerGradeAndCompletesVerifiedLesson() {
        var result = service.attempt(
            courseId,
            lessonId,
            new AssessmentService.AttemptInput(
                enrollmentId,
                List.of(new AssessmentService.Answer(questionId, 0))
            ),
            student
        );
        assertEquals(100.0, result.score());
        assertTrue(result.passed());
        verify(enrollments).complete(enrollmentId, lessonId, section.getId(), student.id());
    }

    @Test
    void failingAttemptDoesNotMarkLessonComplete() {
        var result = service.attempt(
            courseId,
            lessonId,
            new AssessmentService.AttemptInput(
                enrollmentId,
                List.of(new AssessmentService.Answer(questionId, 1))
            ),
            student
        );
        assertFalse(result.passed());
        assertEquals(0.0, result.score());
        verify(enrollments, never()).complete(any(), any(), any(), any());
    }

    @Test
    void foreignEnrollmentAndInventedQuestionAreRejected() {
        when(enrollments.verify(enrollmentId, courseId, student.id())).thenReturn(
            new EnrollmentClient.Result(true, false)
        );
        assertThrows(UnauthorizedException.class, () ->
            service.attempt(
                courseId,
                lessonId,
                new AssessmentService.AttemptInput(
                    enrollmentId,
                    List.of(new AssessmentService.Answer(questionId, 0))
                ),
                student
            )
        );
        when(enrollments.verify(enrollmentId, courseId, student.id())).thenReturn(
            new EnrollmentClient.Result(true, true)
        );
        assertThrows(IllegalArgumentException.class, () ->
            service.attempt(
                courseId,
                lessonId,
                new AssessmentService.AttemptInput(
                    enrollmentId,
                    List.of(new AssessmentService.Answer(UUID.randomUUID(), 0))
                ),
                student
            )
        );
        verify(enrollments, never()).complete(any(), any(), any(), any());
    }

    @Test
    void unrelatedInstructorCannotReadOrWriteAnswerKeys() {
        var stranger = new AuthenticatedUser(UUID.randomUUID(), "Teacher", "INSTRUCTOR");
        assertThrows(UnauthorizedException.class, () -> service.manage(courseId, lessonId, stranger));
        assertThrows(UnauthorizedException.class, () ->
            service.save(courseId, lessonId, new AssessmentService.Quiz(70, List.of()), stranger)
        );
    }
}
