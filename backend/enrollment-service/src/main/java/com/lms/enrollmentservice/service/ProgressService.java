package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.*;
import com.lms.enrollmentservice.domain.enums.*;
import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.*;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LessonProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseCatalogService courseCatalogService;
    private java.time.Clock clock = java.time.Clock.systemUTC();

    public record PlaybackRequest(
        @jakarta.validation.constraints.NotNull UUID sectionId,
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.DecimalMin("0")
        @jakarta.validation.constraints.DecimalMax("172800")
        Double positionSeconds,
        boolean playing
    ) {}

    @Transactional
    public ProgressResponse recordPlayback(
        UUID enrollmentId,
        UUID lessonId,
        PlaybackRequest request,
        UUID studentId
    ) {
        Enrollment enrollment = requireActiveEnrollment(enrollmentId, studentId);
        var course = courseCatalogService.requireCourse(enrollment.getCourseId());
        var lesson = requireLesson(course, request.sectionId(), lessonId);
        int duration = lesson.durationSeconds() == null ? 0 : lesson.durationSeconds();
        if (!"VIDEO".equals(lesson.type()) || duration <= 0) throw new IllegalArgumentException(
            "Video ve geçerli ders süresi gerekli"
        );
        double position = request.positionSeconds();
        if (
            !Double.isFinite(position) || position < 0 || position > duration + 1
        ) throw new IllegalArgumentException("Video konumu ders süresinin dışında");
        position = Math.min(position, duration);
        var progress = getOrCreateProgress(enrollment, lessonId, request.sectionId(), duration);
        LocalDateTime now = LocalDateTime.now(clock);
        if (
            progress.isPlaybackActive() &&
            progress.getLastPlaybackAt() != null &&
            progress.getLastPlaybackPosition() != null
        ) {
            long elapsed = Math.max(
                0,
                java.time.Duration.between(progress.getLastPlaybackAt(), now).toMillis()
            );
            double advanced = (position - progress.getLastPlaybackPosition()) * 1000;
            // Never award a seek or an unbounded background interval. Two-times playback is supported.
            if (advanced > 0 && advanced <= elapsed * 2.0 + 2000) {
                long credit = (long) Math.min(advanced, Math.min(elapsed, 30_000) * 2.0);
                addWatchedRange(progress, Math.max(0, position - credit / 1000.0), position);
            }
        }
        progress.setPositionSeconds((int) position);
        progress.setWatchedSeconds(
            Math.max(progress.getWatchedSeconds() == null ? 0 : progress.getWatchedSeconds(), (int) position)
        );
        progress.setTotalSeconds(duration);
        progress.setLastPlaybackAt(now);
        progress.setLastPlaybackPosition(position);
        progress.setPlaybackActive(request.playing());
        progress.setLastWatchedAt(now);
        if (progress.getStatus() == CompletionStatus.NOT_STARTED) progress.setStatus(
            CompletionStatus.IN_PROGRESS
        );
        // Duration is stored as whole seconds; allow subsecond videos and rounding at the end.
        if (progress.getCreditedMillis() >= Math.max(100, duration * 900L - 900)) completeProgress(progress);
        progressRepository.saveAndFlush(progress);
        updateEnrollmentStatistics(enrollment, course);
        return toResponse(enrollment, progress);
    }

    private void addWatchedRange(LessonProgress progress, double start, double end) {
        if (end <= start) return;
        var ranges = new java.util.ArrayList<double[]>();
        if (progress.getWatchedRanges() != null && !progress.getWatchedRanges().isBlank()) {
            for (String range : progress.getWatchedRanges().split(",")) {
                String[] bounds = range.split(":");
                ranges.add(new double[] { Double.parseDouble(bounds[0]), Double.parseDouble(bounds[1]) });
            }
        }
        ranges.add(new double[] { start, end });
        ranges.sort(java.util.Comparator.comparingDouble(r -> r[0]));
        var merged = new java.util.ArrayList<double[]>();
        for (double[] range : ranges) {
            if (merged.isEmpty() || range[0] > merged.getLast()[1] + 0.001) merged.add(range);
            else merged.getLast()[1] = Math.max(merged.getLast()[1], range[1]);
        }
        progress.setCreditedMillis(
            (long) (merged
                .stream()
                .mapToDouble(r -> r[1] - r[0])
                .sum() * 1000)
        );
        progress.setWatchedRanges(
            merged
                .stream()
                .map(r -> String.format(java.util.Locale.ROOT, "%.3f:%.3f", r[0], r[1]))
                .collect(java.util.stream.Collectors.joining(","))
        );
    }

    @Transactional
    public ProgressResponse updateLessonProgress(
        UUID enrollmentId,
        UUID lessonId,
        UpdateProgressRequest request,
        UUID studentId
    ) {
        Enrollment enrollment = requireActiveEnrollment(enrollmentId, studentId);

        CourseSummaryResponse course = courseCatalogService.requireCourse(enrollment.getCourseId());

        CourseSummaryResponse.Lesson lesson = requireLesson(course, request.getSectionId(), lessonId);

        LessonProgress progress = getOrCreateProgress(
            enrollment,
            lessonId,
            request.getSectionId(),
            lesson.durationSeconds()
        );

        int actualDuration = Math.max(0, lesson.durationSeconds() == null ? 0 : lesson.durationSeconds());

        int previous = progress.getWatchedSeconds() == null ? 0 : progress.getWatchedSeconds();

        int watched = request.getWatchedSeconds() == null ? previous : request.getWatchedSeconds();

        if (watched < previous) {
            throw new IllegalArgumentException("İzleme süresi geriye alınamaz");
        }

        if (actualDuration > 0 && watched > actualDuration) {
            throw new IllegalArgumentException("İzleme süresi ders süresini aşamaz");
        }

        progress.setWatchedSeconds(watched);
        progress.setTotalSeconds(actualDuration);

        if (request.getWatchedSeconds() != null) {
            progress.setLastWatchedAt(LocalDateTime.now());
        }

        if (progress.getStatus() == CompletionStatus.NOT_STARTED) {
            progress.setStatus(CompletionStatus.IN_PROGRESS);
        }

        if (Boolean.TRUE.equals(request.getCompleted())) {
            // ARTICLE tipinde öğrenci manuel olarak okudum diyebilir.
            // Bu bilinçli bir ürün kuralıdır, izleme kanıtı değildir.
            if (!"ARTICLE".equals(lesson.type())) {
                throw new IllegalArgumentException(
                    "Bu ders türünün tamamlanması " + "güvenilir servis tarafından doğrulanmalı"
                );
            }

            completeProgress(progress);
        }

        progressRepository.saveAndFlush(progress);

        updateEnrollmentStatistics(enrollment, course);

        return toResponse(enrollment, progress);
    }

    // Yalnızca güvenilir internal servisten çağrılacak.
    // Çağıran servis gerçek tamamlama koşullarını doğrulamalı.
    @Transactional
    public ProgressResponse completeVerifiedLesson(
        UUID enrollmentId,
        UUID lessonId,
        UUID sectionId,
        UUID studentId
    ) {
        Enrollment enrollment = requireActiveEnrollment(enrollmentId, studentId);

        CourseSummaryResponse course = courseCatalogService.requireCourse(enrollment.getCourseId());

        CourseSummaryResponse.Lesson lesson = requireLesson(course, sectionId, lessonId);

        LessonProgress progress = getOrCreateProgress(
            enrollment,
            lessonId,
            sectionId,
            lesson.durationSeconds()
        );

        completeProgress(progress);

        progressRepository.saveAndFlush(progress);

        updateEnrollmentStatistics(enrollment, course);

        return toResponse(enrollment, progress);
    }

    private Enrollment requireActiveEnrollment(UUID enrollmentId, UUID studentId) {
        Enrollment enrollment = enrollmentRepository
            .findByIdForUpdate(enrollmentId)
            .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));

        if (!enrollment.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException("Kayıt bulunamadı");
        }

        if (
            enrollment.getStatus() != EnrollmentStatus.ACTIVE &&
            enrollment.getStatus() != EnrollmentStatus.COMPLETED
        ) {
            throw new IllegalArgumentException("Kayıt aktif değil");
        }

        return enrollment;
    }

    private CourseSummaryResponse.Lesson requireLesson(
        CourseSummaryResponse course,
        UUID sectionId,
        UUID lessonId
    ) {
        CourseSummaryResponse.Lesson lesson = course.findLesson(sectionId, lessonId);

        if (lesson == null) {
            throw new IllegalArgumentException("Ders belirtilen kursa ve bölüme ait değil");
        }

        return lesson;
    }

    private LessonProgress getOrCreateProgress(
        Enrollment enrollment,
        UUID lessonId,
        UUID sectionId,
        Integer durationSeconds
    ) {
        var existing = progressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);

        if (existing.isPresent()) {
            LessonProgress progress = existing.get();

            if (!progress.getSectionId().equals(sectionId)) {
                progress.setSectionId(sectionId);
            }

            return progress;
        }

        return LessonProgress.builder()
            .enrollment(enrollment)
            .lessonId(lessonId)
            .sectionId(sectionId)
            .watchedSeconds(0)
            .totalSeconds(durationSeconds == null ? 0 : Math.max(0, durationSeconds))
            .status(CompletionStatus.NOT_STARTED)
            .startedAt(LocalDateTime.now())
            .build();
    }

    private void completeProgress(LessonProgress progress) {
        if (progress.getStatus() == CompletionStatus.COMPLETED) {
            return;
        }

        progress.setStatus(CompletionStatus.COMPLETED);

        progress.setCompletedAt(LocalDateTime.now());
    }

    private void updateEnrollmentStatistics(Enrollment enrollment, CourseSummaryResponse course) {
        var lessonIds = course.lessonIds();

        if (lessonIds.isEmpty()) {
            throw new IllegalStateException("Kursun ders listesi boş");
        }

        long completed = progressRepository.countByEnrollmentIdAndStatusAndLessonIdIn(
            enrollment.getId(),
            CompletionStatus.COMPLETED,
            lessonIds
        );

        int total = lessonIds.size();

        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED && completed < total) {
            enrollment.setLastAccessedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            return;
        }
        enrollment.setTotalLessons(total);
        enrollment.setLastAccessedAt(LocalDateTime.now());
        enrollment.setCompletedLessons(Math.toIntExact(completed));

        double percentage = (completed * 100.0) / total;

        enrollment.setProgressPercentage(Math.round(Math.min(100.0, percentage) * 10.0) / 10.0);

        if (completed >= total) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);

            if (enrollment.getCompletedAt() == null) enrollment.setCompletedAt(LocalDateTime.now());

            // Sertifika farklı işlemde üretilecek.
        }

        enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public List<ProgressResponse> getEnrollmentProgress(UUID enrollmentId, UUID studentId) {
        Enrollment enrollment = enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));

        if (!enrollment.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException("Kayıt bulunamadı");
        }

        return progressRepository
            .findByEnrollmentId(enrollmentId)
            .stream()
            .map(progress -> toResponse(enrollment, progress))
            .toList();
    }

    private ProgressResponse toResponse(Enrollment enrollment, LessonProgress progress) {
        return ProgressResponse.builder()
            .enrollmentId(enrollment.getId())
            .lessonId(progress.getLessonId())
            .sectionId(progress.getSectionId())
            .status(progress.getStatus())
            .watchedSeconds(progress.getWatchedSeconds())
            .totalSeconds(progress.getTotalSeconds())
            .positionSeconds(progress.getPositionSeconds())
            .watchedDurationSeconds(progress.getCreditedMillis() / 1000)
            .completedAt(progress.getCompletedAt())
            .lastWatchedAt(progress.getLastWatchedAt())
            .build();
    }
}
