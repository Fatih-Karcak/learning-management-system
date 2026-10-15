package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.entity.LessonProgress;
import com.lms.enrollmentservice.domain.enums.CompletionStatus;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.ProgressResponse;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import com.lms.enrollmentservice.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateService certificateService;
    private final EnrollmentMapper enrollmentMapper;

    @Transactional
    public ProgressResponse updateLessonProgress(
            UUID enrollmentId, UUID lessonId,
            UpdateProgressRequest request, UUID studentId) {

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));

        if (!enrollment.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException("Bu kayda erişim yetkiniz yok");
        }

        LessonProgress progress = lessonProgressRepository
                .findByEnrollmentIdAndLessonId(enrollmentId, lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .enrollment(enrollment)
                        .lessonId(lessonId)
                        .sectionId(request.getSectionId())
                        .status(CompletionStatus.NOT_STARTED)
                        .watchedSeconds(0)
                        .totalSeconds(request.getTotalSeconds())
                        .startedAt(LocalDateTime.now())
                        .build());

        if (request.getWatchedSeconds() != null) {
            progress.setWatchedSeconds(request.getWatchedSeconds());
            progress.setLastWatchedAt(LocalDateTime.now());
        }

        if (progress.getStatus() == CompletionStatus.NOT_STARTED) {
            progress.setStatus(CompletionStatus.IN_PROGRESS);
            progress.setStartedAt(LocalDateTime.now());
        }

        boolean justCompleted = false;
        if (Boolean.TRUE.equals(request.getCompleted())
                && progress.getStatus() != CompletionStatus.COMPLETED) {
            progress.setStatus(CompletionStatus.COMPLETED);
            progress.setCompletedAt(LocalDateTime.now());
            justCompleted = true;
        }

        lessonProgressRepository.save(progress);

        //  DÜZELTME: artık aynı transaction içinde, private metod değil,
        // aynı @Transactional public metod gövdesinde çalışıyor —
        // proxy sorunu ortadan kalktı çünkü ayrı bir metod çağrısına gerek kalmadı.
        if (justCompleted) {
            int completed = lessonProgressRepository.countCompletedLessons(enrollment.getId());
            int total = enrollment.getTotalLessons();

            enrollment.setCompletedLessons(completed);

            if (total > 0) {
                double percentage = (double) completed / total * 100;
                enrollment.setProgressPercentage(Math.round(percentage * 10.0) / 10.0);

                if (completed >= total && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
                    enrollment.setStatus(EnrollmentStatus.COMPLETED);
                    enrollment.setCompletedAt(LocalDateTime.now());
                    enrollmentRepository.save(enrollment);

                    certificateService.generateCertificate(enrollment);
                    log.info("Kurs tamamlandı, sertifika oluşturuluyor: {}", enrollment.getId());
                } else {
                    enrollmentRepository.save(enrollment);
                }
            } else {
                enrollmentRepository.save(enrollment);
            }
        }

        return buildProgressResponse(enrollment, progress);
    }

    public List<ProgressResponse> getEnrollmentProgress(UUID enrollmentId, UUID studentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));

        if (!enrollment.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException("Bu kayda erişim yetkiniz yok");
        }

        return lessonProgressRepository.findByEnrollmentId(enrollmentId)
                .stream()
                .map(p -> buildProgressResponse(enrollment, p))
                .toList();
    }

    private ProgressResponse buildProgressResponse(Enrollment enrollment, LessonProgress progress) {
        return ProgressResponse.builder()
                .enrollmentId(enrollment.getId())
                .lessonId(progress.getLessonId())
                .sectionId(progress.getSectionId())
                .status(progress.getStatus())
                .watchedSeconds(progress.getWatchedSeconds())
                .totalSeconds(progress.getTotalSeconds())
                .completedAt(progress.getCompletedAt())
                .build();
    }
}