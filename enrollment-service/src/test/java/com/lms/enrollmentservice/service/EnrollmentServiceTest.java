
package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.entity.RefundRecord;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.event.RefundCompletedEvent;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import com.lms.enrollmentservice.repository.RefundRecordRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private RefundRecordRepository refundRepository;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private CourseCatalogService courseCatalogService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private PaymentCompletedEvent createPayment() {
        return new PaymentCompletedEvent(
                paymentId,
                studentId,
                courseId,
                "Java Kursu",
                null,
                "Eğitmen",
                BigDecimal.valueOf(500)
        );
    }

    @Test
    void paymentShouldCreateEnrollment() {

        when(enrollmentRepository.findByPaymentId(paymentId))
                .thenReturn(Optional.empty());

        when(enrollmentRepository.findByStudentIdAndCourseId(
                studentId, courseId))
                .thenReturn(Optional.empty());

        when(courseCatalogService.getTotalLessons(courseId))
                .thenReturn(10);

        when(enrollmentRepository.saveAndFlush(
                any(Enrollment.class)))
                .thenAnswer(invocation -> {
                    Enrollment enrollment = invocation.getArgument(0);
                    enrollment.setId(UUID.randomUUID());
                    return enrollment;
                });

        enrollmentService.createEnrollmentFromPayment(
                createPayment()
        );

        verify(enrollmentRepository).saveAndFlush(
                argThat(enrollment ->
                        enrollment.getTotalLessons() == 10
                                && enrollment.getCompletedLessons() == 0
                                && enrollment.getStatus()
                                == EnrollmentStatus.ACTIVE
                                && paymentId.equals(enrollment.getPaymentId())
                )
        );

        verify(outboxService).enqueue(
                eq("course-enrolled"),
                anyString(),
                any()
        );

        verify(outboxService).enqueue(
                eq("enrollment-created"),
                anyString(),
                any()
        );
    }

    @Test
    void duplicatePaymentShouldNotCreateAnotherEnrollment() {

        Enrollment existing = Enrollment.builder()
                .paymentId(paymentId)
                .studentId(studentId)
                .courseId(courseId)
                .build();

        when(enrollmentRepository.findByPaymentId(paymentId))
                .thenReturn(Optional.of(existing));

        enrollmentService.createEnrollmentFromPayment(
                createPayment()
        );

        verify(enrollmentRepository, never())
                .saveAndFlush(any(Enrollment.class));

        verifyNoInteractions(
                outboxService,
                courseCatalogService
        );
    }

    @Test
    void refundShouldChangeEnrollmentStatus() {

        Enrollment enrollment = Enrollment.builder()
                .paymentId(paymentId)
                .studentId(studentId)
                .courseId(courseId)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        when(refundRepository.findById(paymentId))
                .thenReturn(Optional.empty());

        when(enrollmentRepository.findByPaymentId(paymentId))
                .thenReturn(Optional.of(enrollment));

        enrollmentService.handleRefund(
                new RefundCompletedEvent(
                        paymentId,
                        studentId,
                        courseId,
                        UUID.randomUUID()
                )
        );

        assertEquals(
                EnrollmentStatus.REFUNDED,
                enrollment.getStatus()
        );

        verify(refundRepository).saveAndFlush(
                any(RefundRecord.class)
        );
    }

    @Test
    void refundedStudentShouldNotHaveAccess() {

        when(enrollmentRepository
                .existsByStudentIdAndCourseIdAndStatusIn(
                        eq(studentId),
                        eq(courseId),
                        anyList()
                ))
                .thenReturn(false);

        boolean hasAccess = enrollmentService.isEnrolled(
                studentId,
                courseId
        );

        assertFalse(hasAccess);
    }
}
