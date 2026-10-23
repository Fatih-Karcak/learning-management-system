package com.lms.courseservice.service;

import com.lms.courseservice.client.ContentClient;
import com.lms.courseservice.domain.entity.Lesson;
import com.lms.courseservice.domain.entity.Section;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.domain.enums.LessonType;
import com.lms.courseservice.dto.request.CreateLessonRequest;
import com.lms.courseservice.dto.request.UpdateLessonVideoRequest;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.LessonRepository;
import com.lms.courseservice.repository.SectionRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final SectionRepository sectionRepository;
    private final CourseMapper courseMapper;
    private final CourseAccessPolicy accessPolicy;
    private final ContentClient contentClient;

    @Transactional
    public LessonResponse createLesson(CreateLessonRequest request, UUID instructorId) {
        Section section = sectionRepository
            .findById(request.getSectionId())
            .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));

        if (!section.getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu bölüme ders ekleme yetkiniz yok");
        }

        CourseWorkspaceService.requireDirectEditable(section.getCourse());
        int nextOrder = lessonRepository.findBySectionIdOrderByOrderIndexAsc(section.getId()).size();

        Lesson lesson = Lesson.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .videoUrl(request.getVideoUrl())
            .durationSeconds(request.getDurationSeconds())
            .type(request.getType())
            .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : nextOrder)
            .previewAllowed(request.isPreviewAllowed())
            .section(section)
            .build();

        lesson = lessonRepository.save(lesson);
        if (
            lesson.getType() == LessonType.VIDEO &&
            lesson.getVideoUrl() != null &&
            !lesson.getVideoUrl().isBlank()
        ) {
            linkVideo(lesson, lesson.getVideoUrl(), request.getDurationSeconds(), instructorId);
        }
        return courseMapper.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse updateVideo(UUID lessonId, UpdateLessonVideoRequest request, UUID instructorId) {
        Lesson lesson = lessonRepository
            .findById(lessonId)
            .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));
        if (!lesson.getSection().getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu dersin videosunu değiştirme yetkiniz yok");
        }
        CourseWorkspaceService.requireDirectEditable(lesson.getSection().getCourse());
        if (lesson.getType() != LessonType.VIDEO && lesson.getType() != LessonType.ARTICLE) {
            throw new IllegalArgumentException(
                "Video yalnızca video veya okuma türündeki bir derse eklenebilir"
            );
        }
        linkVideo(lesson, request.videoUrl(), request.durationSeconds(), instructorId);
        lesson.setType(LessonType.VIDEO);
        return courseMapper.toLessonResponse(lessonRepository.save(lesson));
    }

    private void linkVideo(Lesson lesson, String url, Integer duration, UUID instructorId) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Video adresi geçersiz");
        }
        if (
            uri.isAbsolute() &&
            !"https".equalsIgnoreCase(uri.getScheme()) &&
            !"http".equalsIgnoreCase(uri.getScheme())
        ) {
            throw new IllegalArgumentException("Video adresi http veya https olmalı");
        }
        var match = java.util.regex.Pattern.compile(
            "^/api/v1/media/([0-9a-fA-F-]{36})/manifest\\.m3u8$"
        ).matcher(uri.getPath() == null ? "" : uri.getPath());
        if (match.matches()) {
            UUID mediaId = UUID.fromString(match.group(1));
            ContentClient.Result result;
            try {
                result = contentClient.linkVideo(
                    mediaId,
                    new ContentClient.LinkRequest(
                        lesson.getSection().getCourse().getId(),
                        lesson.getId(),
                        instructorId
                    )
                );
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 403) throw new UnauthorizedException(
                    "Yalnızca kendi videonuzu kullanabilirsiniz"
                );
                if (ex.getStatusCode().value() == 404) throw new CourseNotFoundException("Video bulunamadı");
                if (ex.getStatusCode().value() == 409) throw new IllegalStateException(
                    "Video işlenemedi veya başka bir kursa bağlı"
                );
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Video derse bağlanamadı; içerik servisini kontrol edin",
                    ex
                );
            } catch (RestClientException ex) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "İçerik servisine ulaşılamadı",
                    ex
                );
            }
            if (result == null || !result.success() || result.data() == null) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Video bağlantısı doğrulanamadı"
                );
            }
            // Store a relative gateway URL, independent of an instructor's local host/port.
            lesson.setVideoUrl("/api/v1/media/" + mediaId + "/manifest.m3u8");
            lesson.setDurationSeconds(
                result.data().durationSeconds() != null ? result.data().durationSeconds() : duration
            );
        } else {
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException(
                    "Medya kitaplığından bir video veya geçerli bir video adresi seçin"
                );
            }
            lesson.setVideoUrl(url.trim());
            lesson.setDurationSeconds(duration);
        }
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getLessonsBySection(UUID sectionId, AuthenticatedUser viewer) {
        Section section = sectionRepository
            .findById(sectionId)
            .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));
        accessPolicy.requireVisible(section.getCourse(), viewer);
        boolean fullAccess = accessPolicy.canReadContent(section.getCourse(), viewer);
        List<LessonResponse> responses = lessonRepository
            .findBySectionIdOrderByOrderIndexAsc(sectionId)
            .stream()
            .map(courseMapper::toLessonResponse)
            .toList();
        accessPolicy.filterLessons(responses, fullAccess);
        return responses;
    }

    @Transactional
    public void deleteLesson(UUID lessonId, UUID instructorId) {
        Lesson lesson = lessonRepository
            .findById(lessonId)
            .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));

        if (!lesson.getSection().getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu dersi silme yetkiniz yok");
        }

        if (
            lesson.getSection().getCourse().getStatus() == CourseStatus.PUBLISHED &&
            lessonRepository.countBySectionCourseId(lesson.getSection().getCourse().getId()) <= 1
        ) {
            throw new IllegalStateException("Yayımlanmış kursun son dersi silinemez");
        }
        CourseWorkspaceService.requireDirectEditable(lesson.getSection().getCourse());
        lessonRepository.delete(lesson);
    }
}
