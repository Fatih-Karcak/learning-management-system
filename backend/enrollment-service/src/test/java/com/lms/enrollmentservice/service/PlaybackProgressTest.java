package com.lms.enrollmentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.enrollmentservice.domain.entity.*;
import com.lms.enrollmentservice.domain.enums.*;
import com.lms.enrollmentservice.dto.response.CourseSummaryResponse;
import com.lms.enrollmentservice.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaybackProgressTest {

    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final LessonProgressRepository records = mock(LessonProgressRepository.class);
    final CourseCatalogService catalog = mock(CourseCatalogService.class);
    final ProgressService service = new ProgressService(records, enrollments, catalog);
    final UUID student = UUID.randomUUID(),
        section = UUID.randomUUID(),
        lesson = UUID.randomUUID();
    final Enrollment enrollment = Enrollment.builder()
        .id(UUID.randomUUID())
        .studentId(student)
        .courseId(UUID.randomUUID())
        .status(EnrollmentStatus.ACTIVE)
        .build();
    final LessonProgress progress = LessonProgress.builder()
        .enrollment(enrollment)
        .lessonId(lesson)
        .sectionId(section)
        .status(CompletionStatus.NOT_STARTED)
        .watchedSeconds(0)
        .totalSeconds(100)
        .build();
    final Instant start = Instant.parse("2026-10-22T12:00:00Z");

    PlaybackProgressTest() {
        when(enrollments.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(records.findByEnrollmentIdAndLessonId(enrollment.getId(), lesson)).thenReturn(
            Optional.of(progress)
        );
        when(catalog.requireCourse(enrollment.getCourseId())).thenReturn(
            new CourseSummaryResponse(
                enrollment.getCourseId(),
                "Course",
                null,
                "Teacher",
                null,
                "PUBLISHED",
                List.of(
                    new CourseSummaryResponse.Section(
                        section,
                        List.of(new CourseSummaryResponse.Lesson(lesson, 100, "VIDEO"))
                    )
                )
            )
        );
        when(records.countByEnrollmentIdAndStatusAndLessonIdIn(any(), any(), any())).thenAnswer(call ->
            progress.getStatus() == CompletionStatus.COMPLETED ? 1L : 0L
        );
    }

    void report(int time, double position, boolean playing) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(start.plusSeconds(time), ZoneOffset.UTC));
        service.recordPlayback(
            enrollment.getId(),
            lesson,
            new ProgressService.PlaybackRequest(section, position, playing),
            student
        );
    }

    @Test
    void seekingToEndDoesNotCompleteOrCreditUnwatchedVideo() {
        report(0, 0, true);
        report(1, 99, true);
        assertEquals(0, progress.getCreditedMillis());
        assertEquals(CompletionStatus.IN_PROGRESS, progress.getStatus());
        assertEquals(99, progress.getPositionSeconds());
        assertEquals(0.0, enrollment.getProgressPercentage());
    }

    @Test
    void realPlaybackCompletesCourseAtThresholdAndIsIdempotent() {
        report(0, 0, true);
        for (int i = 1; i <= 9; i++) report(i * 10, i * 10, true);
        assertEquals(CompletionStatus.COMPLETED, progress.getStatus());
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertEquals(100.0, enrollment.getProgressPercentage());
        assertEquals(90_000, progress.getCreditedMillis());
        report(91, 90, false);
        assertEquals(90_000, progress.getCreditedMillis());
        assertEquals(1, enrollment.getCompletedLessons());
    }

    @Test
    void repeatedAndOverlappingSegmentsAreNotCountedTwice() {
        report(0, 0, true);
        report(10, 10, true);
        report(11, 0, false);
        report(12, 0, true);
        report(22, 10, true);
        assertEquals(10_000, progress.getCreditedMillis());
        report(27, 15, true);
        assertEquals(15_000, progress.getCreditedMillis());
    }

    @Test
    void pausedTimeAndRapidRequestsCannotCreateWatchCredit() {
        report(0, 0, false);
        report(100, 50, true);
        assertEquals(0, progress.getCreditedMillis());
        report(100, 51, true);
        assertEquals(0, progress.getCreditedMillis());
    }

    @Test
    void anotherStudentsEnrollmentAndOutOfRangePositionsAreRejected() {
        assertThrows(com.lms.enrollmentservice.exception.EnrollmentNotFoundException.class, () ->
            service.recordPlayback(
                enrollment.getId(),
                lesson,
                new ProgressService.PlaybackRequest(section, 1.0, true),
                UUID.randomUUID()
            )
        );
        assertThrows(IllegalArgumentException.class, () -> report(0, 102, true));
        verify(records, never()).saveAndFlush(any());
    }
}
