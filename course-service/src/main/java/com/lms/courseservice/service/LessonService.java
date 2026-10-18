package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Lesson;
import com.lms.courseservice.domain.entity.Section;
import com.lms.courseservice.dto.request.CreateLessonRequest;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.LessonRepository;
import com.lms.courseservice.repository.SectionRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.domain.enums.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final SectionRepository sectionRepository;
    private final CourseMapper courseMapper;
    private final CourseAccessPolicy accessPolicy;

    @Transactional
    public LessonResponse createLesson(CreateLessonRequest request, UUID instructorId) {
        Section section = sectionRepository.findById(request.getSectionId())
                .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));

        if (!section.getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu bölüme ders ekleme yetkiniz yok");
        }

        int nextOrder = lessonRepository
                .findBySectionIdOrderByOrderIndexAsc(section.getId()).size();

        Lesson lesson = Lesson.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .videoUrl(request.getVideoUrl())
                .durationSeconds(request.getDurationSeconds())
                .type(request.getType())
                .orderIndex(request.getOrderIndex() != null
                        ? request.getOrderIndex() : nextOrder)
                .previewAllowed(request.isPreviewAllowed())
                .section(section)
                .build();

        return courseMapper.toLessonResponse(lessonRepository.save(lesson));
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getLessonsBySection(UUID sectionId, AuthenticatedUser viewer) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));
        accessPolicy.requireVisible(section.getCourse(), viewer);
        boolean fullAccess = accessPolicy.canReadContent(section.getCourse(), viewer);
        List<LessonResponse> responses = lessonRepository.findBySectionIdOrderByOrderIndexAsc(sectionId)
                .stream()
                .map(courseMapper::toLessonResponse)
                .toList();
        accessPolicy.filterLessons(responses, fullAccess);
        return responses;
    }

    @Transactional
    public void deleteLesson(UUID lessonId, UUID instructorId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));

        if (!lesson.getSection().getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu dersi silme yetkiniz yok");
        }

        if (lesson.getSection().getCourse().getStatus() == CourseStatus.PUBLISHED
                && lessonRepository.countBySectionCourseId(lesson.getSection().getCourse().getId()) <= 1) {
            throw new IllegalStateException("Yayımlanmış kursun son dersi silinemez");
        }
        lessonRepository.delete(lesson);
    }
}
