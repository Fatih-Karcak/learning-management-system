package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.entity.RefundRecord;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.response.EnrollmentResponse;
import com.lms.enrollmentservice.event.CourseEnrolledEvent;
import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.event.RefundCompletedEvent;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import com.lms.enrollmentservice.repository.RefundRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

 private final EnrollmentRepository enrollmentRepository;
 private final RefundRecordRepository refundRepository;
 private final EnrollmentMapper enrollmentMapper;
 private final CourseCatalogService courseCatalogService;
 private final OutboxService outboxService;

 @Transactional
 public void createEnrollmentFromPayment(
         PaymentCompletedEvent event) {

  if (event == null
          || event.paymentId() == null
          || event.studentId() == null
          || event.courseId() == null
          || event.amount() == null
          || event.amount().signum() < 0) {

   throw new IllegalArgumentException(
           "Geçersiz payment-completed olayı"
   );
  }

  // Aynı ödeme Kafka üzerinden tekrar gelebilir.
  if (enrollmentRepository
          .findByPaymentId(event.paymentId())
          .isPresent()) {

   log.info(
           "Ödeme daha önce işlendi: {}",
           event.paymentId()
   );
   return;
  }

  // Aynı öğrenci-kurs için ikinci ödeme ayrıca ele alınmalı.
  var existing = enrollmentRepository
          .findByStudentIdAndCourseId(
                  event.studentId(),
                  event.courseId()
          );

  if (existing.isPresent()) {
   if (!event.paymentId().equals(
           existing.get().getPaymentId())) {

    throw new IllegalStateException(
            "Kurs için farklı ödeme kaydı mevcut. " +
                    "Mutabakat gerekli."
    );
   }
   return;
  }

  boolean refunded = refundRepository
          .existsById(event.paymentId());

  int totalLessons = 0;

  if (!refunded) {
   totalLessons = courseCatalogService
           .getTotalLessons(event.courseId());
  }

  Enrollment enrollment = Enrollment.builder()
          .paymentId(event.paymentId())
          .studentId(event.studentId())
          .courseId(event.courseId())
          .courseTitle(event.courseTitle())
          .courseThumbnailUrl(
                  event.courseThumbnailUrl()
          )
          .instructorName(event.instructorName())
          .pricePaid(event.amount())
          .status(
                  refunded
                          ? EnrollmentStatus.REFUNDED
                          : EnrollmentStatus.ACTIVE
          )
          .totalLessons(totalLessons)
          .completedLessons(0)
          .progressPercentage(0.0)
          .build();

  enrollment = enrollmentRepository
          .saveAndFlush(enrollment);

  if (refunded) {
   log.info(
           "İadesi önceden alınmış ödeme: {}",
           event.paymentId()
   );
   return;
  }

  CourseEnrolledEvent created =
          new CourseEnrolledEvent(
                  enrollment.getId(),
                  event.studentId(),
                  event.courseId(),
                  null,
                  event.courseTitle()
          );

  outboxService.enqueue(
          "course-enrolled",
          event.courseId().toString(),
          created
  );

  outboxService.enqueue(
          "enrollment-created",
          enrollment.getId().toString(),
          created
  );

  log.info(
          "Enrollment oluşturuldu: {}",
          enrollment.getId()
  );
 }

 @Transactional
 public void handleRefund(
         RefundCompletedEvent event) {

  if (event == null
          || event.paymentId() == null
          || event.studentId() == null
          || event.courseId() == null
          || event.refundId() == null) {

   throw new IllegalArgumentException(
           "Geçersiz refund olayı"
   );
  }

  var existingRefund = refundRepository
          .findById(event.paymentId());

  if (existingRefund.isPresent()) {
   RefundRecord refund = existingRefund.get();

   if (!refund.getStudentId().equals(event.studentId())
           || !refund.getCourseId().equals(event.courseId())) {

    throw new IllegalStateException(
            "Aynı ödeme için çelişkili iade bilgisi"
    );
   }
  } else {
   refundRepository.saveAndFlush(
           new RefundRecord(
                   event.paymentId(),
                   event.studentId(),
                   event.courseId(),
                   event.refundId()
           )
   );
  }

  enrollmentRepository
          .findByPaymentId(event.paymentId())
          .ifPresent(enrollment -> {

           if (!enrollment.getStudentId()
                   .equals(event.studentId())
                   || !enrollment.getCourseId()
                   .equals(event.courseId())) {

            throw new IllegalStateException(
                    "İade ile kayıt bilgileri uyuşmuyor"
            );
           }

           enrollment.setStatus(
                   EnrollmentStatus.REFUNDED
           );
          });
 }

 @Transactional
 public void reconcileRefund(RefundRecord refund) {

  enrollmentRepository
          .findByPaymentId(refund.getPaymentId())
          .ifPresent(enrollment -> {

           if (!enrollment.getStudentId()
                   .equals(refund.getStudentId())
                   || !enrollment.getCourseId()
                   .equals(refund.getCourseId())) {

            throw new IllegalStateException(
                    "İade kaydı tutarsız"
            );
           }

           if (enrollment.getStatus()
                   != EnrollmentStatus.CANCELLED) {

            enrollment.setStatus(
                    EnrollmentStatus.REFUNDED
            );
           }
          });
 }

 @Transactional(readOnly = true)
 public EnrollmentResponse getEnrollment(
         UUID enrollmentId,
         UUID studentId) {

  Enrollment enrollment = enrollmentRepository
          .findById(enrollmentId)
          .orElseThrow(() ->
                  new EnrollmentNotFoundException(
                          "Kayıt bulunamadı"
                  )
          );

  if (!enrollment.getStudentId().equals(studentId)) {
   throw new EnrollmentNotFoundException(
           "Kayıt bulunamadı"
   );
  }

  return enrollmentMapper.toResponse(enrollment);
 }

 @Transactional(readOnly = true)
 public EnrollmentResponse getEnrollmentByCourse(
         UUID studentId,
         UUID courseId) {

  Enrollment enrollment = enrollmentRepository
          .findByStudentIdAndCourseId(
                  studentId, courseId
          )
          .orElseThrow(() ->
                  new EnrollmentNotFoundException(
                          "Kayıt bulunamadı"
                  )
          );

  return enrollmentMapper.toResponse(enrollment);
 }

 @Transactional(readOnly = true)
 public Page<EnrollmentResponse> getStudentEnrollments(
         UUID studentId,
         Pageable pageable) {

  return enrollmentRepository
          .findByStudentId(studentId, pageable)
          .map(enrollmentMapper::toResponse);
 }

 @Transactional(readOnly = true)
 public boolean isEnrolled(
         UUID studentId,
         UUID courseId) {

  return enrollmentRepository
          .existsByStudentIdAndCourseIdAndStatusIn(
                  studentId,
                  courseId,
                  List.of(
                          EnrollmentStatus.ACTIVE,
                          EnrollmentStatus.COMPLETED
                  )
          );
 }

 @Transactional(readOnly=true)
 public boolean verifyEnrollment(UUID enrollmentId,UUID courseId,UUID studentId) {
  return enrollmentRepository.findById(enrollmentId).filter(e->e.getCourseId().equals(courseId)&&e.getStudentId().equals(studentId)
          &&(e.getStatus()==EnrollmentStatus.ACTIVE||e.getStatus()==EnrollmentStatus.COMPLETED)).isPresent();
 }

 @Transactional
 public void updateLastAccessed(UUID enrollmentId) {

  enrollmentRepository.findById(enrollmentId)
          .ifPresent(enrollment -> {

           if (enrollment.getStatus()
                   == EnrollmentStatus.ACTIVE
                   || enrollment.getStatus()
                   == EnrollmentStatus.COMPLETED) {

            enrollment.setLastAccessedAt(
                    LocalDateTime.now()
            );
           }
          });
 }
}
