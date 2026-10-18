
package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.CourseSummaryResponse;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import com.lms.enrollmentservice.repository.LessonProgressRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private LessonProgressRepository progressRepository;

    @Mock
    private CourseCatalogService courseCatalogService;

    @InjectMocks
    private ProgressService progressService;

    @Test
    void lessonFromAnotherSectionShouldBeRejected() {

        UUID enrollmentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID realSectionId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        Enrollment enrollment = Enrollment.builder()
                .id(enrollmentId)
                .studentId(studentId)
                .courseId(courseId)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        when(enrollmentRepository.findByIdForUpdate(enrollmentId))
                .thenReturn(Optional.of(enrollment));

        CourseSummaryResponse course =
                new CourseSummaryResponse(
                        courseId,
                        "Java",
                        null,
                        "Eğitmen",
                        null,
                        "PUBLISHED",
                        List.of(
                                new CourseSummaryResponse.Section(
                                        realSectionId,
                                        List.of(
                                                new CourseSummaryResponse.Lesson(
                                                        lessonId, 600, "VIDEO"
                                                )
                                        )
                                )
                        )
                );

        when(courseCatalogService.requireCourse(courseId))
                .thenReturn(course);

        UpdateProgressRequest request =
                new UpdateProgressRequest();

        request.setSectionId(UUID.randomUUID());
        request.setWatchedSeconds(100);
        request.setCompleted(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> progressService.updateLessonProgress(
                        enrollmentId,
                        lessonId,
                        request,
                        studentId
                )
        );

        verify(progressRepository, never())
                .saveAndFlush(any());
    }

    @Test
    void refundedEnrollmentShouldRejectProgressUpdate() {

        UUID enrollmentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Enrollment enrollment = Enrollment.builder()
                .id(enrollmentId)
                .studentId(studentId)
                .status(EnrollmentStatus.REFUNDED)
                .build();

        when(enrollmentRepository.findByIdForUpdate(enrollmentId))
                .thenReturn(Optional.of(enrollment));

        UpdateProgressRequest request =
                new UpdateProgressRequest();

        request.setSectionId(UUID.randomUUID());

        assertThrows(
                IllegalArgumentException.class,
                () -> progressService.updateLessonProgress(
                        enrollmentId,
                        UUID.randomUUID(),
                        request,
                        studentId
                )
        );

        verifyNoInteractions(courseCatalogService);
    }
}
