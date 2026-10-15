package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.entity.LessonProgress;
import com.lms.enrollmentservice.domain.enums.CompletionStatus;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.response.EnrollmentResponse;
import com.lms.enrollmentservice.event.CourseEnrolledEvent;
import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

 private final EnrollmentRepository enrollmentRepository;
 private final EnrollmentMapper enrollmentMapper;
 private final KafkaTemplate<String, Object> kafkaTemplate;

 @Transactional
 public void createEnrollmentFromPayment(PaymentCompletedEvent event) {
  // Mükerrer kayıt kontrolü
  if (enrollmentRepository.existsByStudentIdAndCourseId(
          event.studentId(), event.courseId())) {
   log.warn("Zaten kayıtlı - StudentId: {}, CourseId: {}",
           event.studentId(), event.courseId());
   return;
  }

  Enrollment enrollment = Enrollment.builder()
          .studentId(event.studentId())
          .courseId(event.courseId())
          .courseTitle(event.courseTitle())
          .courseThumbnailUrl(event.courseThumbnailUrl())
          .instructorName(event.instructorName())
          .pricePaid(event.amount())
          .status(EnrollmentStatus.ACTIVE)
          .totalLessons(0)
          .completedLessons(0)
          .progressPercentage(0.0)
          .build();

  enrollment = enrollmentRepository.save(enrollment);
  log.info("Enrollment oluşturuldu: {}", enrollment.getId());

  // Course Service'e enrollment sayısını güncelle
  kafkaTemplate.send("course-enrolled",
          event.courseId().toString(),
          new CourseEnrolledEvent(
                  enrollment.getId(),
                  event.studentId(),
                  event.courseId(),
                  null, // email Notification Service tarafından alınacak
                  event.courseTitle()
          ));

  // Notification Service'e bildir
  kafkaTemplate.send("enrollment-created",
          enrollment.getId().toString(),
          new CourseEnrolledEvent(
                  enrollment.getId(),
                  event.studentId(),
                  event.courseId(),
                  null,
                  event.courseTitle()
          ));
 }

 public EnrollmentResponse getEnrollment(UUID enrollmentId, UUID studentId) {
  Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
          .orElseThrow(() -> new EnrollmentNotFoundException(
                  "Kayıt bulunamadı: " + enrollmentId));

  if (!enrollment.getStudentId().equals(studentId)) {
   throw new EnrollmentNotFoundException("Bu kayda erişim yetkiniz yok");
  }

  return enrollmentMapper.toResponse(enrollment);
 }

 public EnrollmentResponse getEnrollmentByCourse(UUID studentId, UUID courseId) {
  return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)
          .map(enrollmentMapper::toResponse)
          .orElseThrow(() -> new EnrollmentNotFoundException(
                  "Bu kursa kayıtlı değilsiniz"));
 }

 public Page<EnrollmentResponse> getStudentEnrollments(
         UUID studentId, Pageable pageable) {
  return enrollmentRepository.findByStudentId(studentId, pageable)
          .map(enrollmentMapper::toResponse);
 }

 public boolean isEnrolled(UUID studentId, UUID courseId) {
  return enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId);
 }

 @Transactional
 public void updateLastAccessed(UUID enrollmentId) {
  enrollmentRepository.findById(enrollmentId).ifPresent(e -> {
   e.setLastAccessedAt(LocalDateTime.now());
   enrollmentRepository.save(e);
  });
 }
}