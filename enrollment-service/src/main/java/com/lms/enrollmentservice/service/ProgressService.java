package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.*;
import com.lms.enrollmentservice.domain.enums.*;
import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.*;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LessonProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseCatalogService courseCatalogService;

    @Transactional
    public ProgressResponse updateLessonProgress(
            UUID enrollmentId,
            UUID lessonId,
            UpdateProgressRequest request,
            UUID studentId) {

        Enrollment enrollment =
                requireActiveEnrollment(enrollmentId, studentId);

        CourseSummaryResponse course =
                courseCatalogService.requireCourse(
                        enrollment.getCourseId()
                );

        CourseSummaryResponse.Lesson lesson =
                requireLesson(
                        course,
                        request.getSectionId(),
                        lessonId
                );

        LessonProgress progress =
                getOrCreateProgress(
                        enrollment,
                        lessonId,
                        request.getSectionId(),
                        lesson.durationSeconds()
                );

        int actualDuration = Math.max(
                0,
                lesson.durationSeconds() == null
                        ? 0
                        : lesson.durationSeconds()
        );

        int previous = progress.getWatchedSeconds() == null
                ? 0
                : progress.getWatchedSeconds();

        int watched = request.getWatchedSeconds() == null
                ? previous
                : request.getWatchedSeconds();

        if (watched < previous) {
            throw new IllegalArgumentException(
                    "İzleme süresi geriye alınamaz"
            );
        }

        if (actualDuration > 0 && watched > actualDuration) {
            throw new IllegalArgumentException(
                    "İzleme süresi ders süresini aşamaz"
            );
        }

        progress.setWatchedSeconds(watched);
        progress.setTotalSeconds(actualDuration);

        if (request.getWatchedSeconds() != null) {
            progress.setLastWatchedAt(
                    LocalDateTime.now()
            );
        }

        if (progress.getStatus()
                == CompletionStatus.NOT_STARTED) {

            progress.setStatus(
                    CompletionStatus.IN_PROGRESS
            );
        }

        if (Boolean.TRUE.equals(request.getCompleted())) {

            // ARTICLE tipinde öğrenci manuel olarak okudum diyebilir.
            // Bu bilinçli bir ürün kuralıdır, izleme kanıtı değildir.
            if (!"ARTICLE".equals(lesson.type())) {

                throw new IllegalArgumentException(
                        "Bu ders türünün tamamlanması " +
                                "güvenilir servis tarafından doğrulanmalı"
                );
            }

            completeProgress(progress);
        }

        progressRepository.saveAndFlush(progress);

        updateEnrollmentStatistics(
                enrollment,
                course
        );

        return toResponse(enrollment, progress);
    }

    // Yalnızca güvenilir internal servisten çağrılacak.
    // Çağıran servis gerçek tamamlama koşullarını doğrulamalı.
    @Transactional
    public ProgressResponse completeVerifiedLesson(
            UUID enrollmentId,
            UUID lessonId,
            UUID sectionId,
            UUID studentId) {

        Enrollment enrollment =
                requireActiveEnrollment(enrollmentId, studentId);

        CourseSummaryResponse course =
                courseCatalogService.requireCourse(
                        enrollment.getCourseId()
                );

        CourseSummaryResponse.Lesson lesson =
                requireLesson(
                        course, sectionId, lessonId
                );

        LessonProgress progress =
                getOrCreateProgress(
                        enrollment,
                        lessonId,
                        sectionId,
                        lesson.durationSeconds()
                );

        completeProgress(progress);

        progressRepository.saveAndFlush(progress);

        updateEnrollmentStatistics(
                enrollment,
                course
        );

        return toResponse(enrollment, progress);
    }

    private Enrollment requireActiveEnrollment(
            UUID enrollmentId,
            UUID studentId) {

        Enrollment enrollment = enrollmentRepository
                .findByIdForUpdate(enrollmentId)
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

        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Kayıt aktif değil"
            );
        }

        return enrollment;
    }

    private CourseSummaryResponse.Lesson requireLesson(
            CourseSummaryResponse course,
            UUID sectionId,
            UUID lessonId) {

        CourseSummaryResponse.Lesson lesson =
                course.findLesson(sectionId, lessonId);

        if (lesson == null) {
            throw new IllegalArgumentException(
                    "Ders belirtilen kursa ve bölüme ait değil"
            );
        }

        return lesson;
    }

    private LessonProgress getOrCreateProgress(
            Enrollment enrollment,
            UUID lessonId,
            UUID sectionId,
            Integer durationSeconds) {

        var existing = progressRepository
                .findByEnrollmentIdAndLessonId(
                        enrollment.getId(),
                        lessonId
                );

        if (existing.isPresent()) {
            LessonProgress progress = existing.get();

            if (!progress.getSectionId().equals(sectionId)) {
                throw new IllegalArgumentException(
                        "Ders bölüm kimliği uyuşmuyor"
                );
            }

            return progress;
        }

        return LessonProgress.builder()
                .enrollment(enrollment)
                .lessonId(lessonId)
                .sectionId(sectionId)
                .watchedSeconds(0)
                .totalSeconds(
                        durationSeconds == null
                                ? 0
                                : Math.max(0, durationSeconds)
                )
                .status(CompletionStatus.NOT_STARTED)
                .startedAt(LocalDateTime.now())
                .build();
    }

    private void completeProgress(LessonProgress progress) {

        if (progress.getStatus()
                == CompletionStatus.COMPLETED) {
            return;
        }

        progress.setStatus(
                CompletionStatus.COMPLETED
        );

        progress.setCompletedAt(
                LocalDateTime.now()
        );
    }

    private void updateEnrollmentStatistics(
            Enrollment enrollment,
            CourseSummaryResponse course) {

        var lessonIds = course.lessonIds();

        if (lessonIds.isEmpty()) {
            throw new IllegalStateException(
                    "Kursun ders listesi boş"
            );
        }

        long completed =
                progressRepository
                        .countByEnrollmentIdAndStatusAndLessonIdIn(
                                enrollment.getId(),
                                CompletionStatus.COMPLETED,
                                lessonIds
                        );

        int total = lessonIds.size();

        enrollment.setTotalLessons(total);
        enrollment.setCompletedLessons(
                Math.toIntExact(completed)
        );

        double percentage =
                (completed * 100.0) / total;

        enrollment.setProgressPercentage(
                Math.round(
                        Math.min(100.0, percentage) * 10.0
                ) / 10.0
        );

        if (completed >= total) {

            enrollment.setStatus(
                    EnrollmentStatus.COMPLETED
            );

            enrollment.setCompletedAt(
                    LocalDateTime.now()
            );

            // Sertifika farklı işlemde üretilecek.
        }

        enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public List<ProgressResponse> getEnrollmentProgress(
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

        return progressRepository
                .findByEnrollmentId(enrollmentId)
                .stream()
                .map(progress ->
                        toResponse(enrollment, progress))
                .toList();
    }

    private ProgressResponse toResponse(
            Enrollment enrollment,
            LessonProgress progress) {

        return ProgressResponse.builder()
                .enrollmentId(enrollment.getId())
                .lessonId(progress.getLessonId())
                .sectionId(progress.getSectionId())
                .status(progress.getStatus())
                .watchedSeconds(
                        progress.getWatchedSeconds()
                )
                .totalSeconds(progress.getTotalSeconds())
                .completedAt(progress.getCompletedAt())
                .build();
    }
}