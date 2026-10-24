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
            .orElseThrow(() -> new CourseNotFoundException("Section not found"));

        if (!section.getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("You do not have permission to add lessons to this section");
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
            .orElseThrow(() -> new CourseNotFoundException("Lesson not found"));
        if (!lesson.getSection().getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("You do not have permission to change this lesson's video");
        }
        CourseWorkspaceService.requireDirectEditable(lesson.getSection().getCourse());
        if (lesson.getType() != LessonType.VIDEO && lesson.getType() != LessonType.ARTICLE) {
            throw new IllegalArgumentException(
                "A video can only be attached to a video or article lesson"
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
            throw new IllegalArgumentException("Invalid video URL");
        }
        if (
            uri.isAbsolute() &&
            !"https".equalsIgnoreCase(uri.getScheme()) &&
            !"http".equalsIgnoreCase(uri.getScheme())
        ) {
            throw new IllegalArgumentException("Video URLs must use HTTP or HTTPS");
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
                    "You can only use your own videos"
                );
                if (ex.getStatusCode().value() == 404) throw new CourseNotFoundException("Video not found");
                if (ex.getStatusCode().value() == 409) throw new IllegalStateException(
                    "The video failed processing or belongs to another course"
                );
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The video could not be attached; check the content service",
                    ex
                );
            } catch (RestClientException ex) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The content service could not be reached",
                    ex
                );
            }
            if (result == null || !result.success() || result.data() == null) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The video attachment could not be verified"
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
                    "Select a video from the media library or provide a valid video URL"
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
            .orElseThrow(() -> new CourseNotFoundException("Section not found"));
        accessPolicy.requireVisible(section.getCourse(), viewer);
        boolean fullAccess = accessPolicy.canReadContent(section.getCourse(), viewer);
        List<LessonResponse> responses = lessonRepository
            .findBySectionIdOrderByOrderIndexAsc(sectionId)
            .stream()
            .map(courseMapper::toLessonResponse)
            .toList();
        accessPolicy.filterCourseLessons(section.getCourse(), responses, fullAccess, viewer);
        return responses;
    }

    @Transactional
    public void deleteLesson(UUID lessonId, UUID instructorId) {
        Lesson lesson = lessonRepository
            .findById(lessonId)
            .orElseThrow(() -> new CourseNotFoundException("Lesson not found"));

        if (!lesson.getSection().getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu dersi silme yetkiniz yok");
        }

        if (
            lesson.getSection().getCourse().getStatus() == CourseStatus.PUBLISHED &&
            lessonRepository.countBySectionCourseId(lesson.getSection().getCourse().getId()) <= 1
        ) {
            throw new IllegalStateException("The last lesson of a published course cannot be deleted");
        }
        CourseWorkspaceService.requireDirectEditable(lesson.getSection().getCourse());
        lessonRepository.delete(lesson);
    }
}
