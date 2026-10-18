package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.entity.Section;
import com.lms.courseservice.dto.request.CreateSectionRequest;
import com.lms.courseservice.dto.response.SectionResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.repository.SectionRepository;
import com.lms.courseservice.repository.LessonRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.domain.enums.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final CourseAccessPolicy accessPolicy;
    private final LessonRepository lessonRepository;

    @Transactional
    public SectionResponse createSection(CreateSectionRequest request, UUID instructorId) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı"));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu kursa bölüm ekleme yetkiniz yok");
        }

        int nextOrder = sectionRepository
                .findByCourseIdOrderByOrderIndexAsc(course.getId()).size();

        Section section = Section.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .orderIndex(request.getOrderIndex() != null
                        ? request.getOrderIndex() : nextOrder)
                .course(course)
                .build();

        return courseMapper.toSectionResponse(sectionRepository.save(section));
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> getSectionsByCourse(UUID courseId, AuthenticatedUser viewer) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı"));
        accessPolicy.requireVisible(course, viewer);
        boolean fullAccess = accessPolicy.canReadContent(course, viewer);
        return sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream()
                .map(courseMapper::toSectionResponse)
                .peek(section -> accessPolicy.filterLessons(section.getLessons(), fullAccess))
                .toList();
    }

    @Transactional
    public void deleteSection(UUID sectionId, UUID instructorId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));

        if (!section.getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu bölümü silme yetkiniz yok");
        }

        if (section.getCourse().getStatus() == CourseStatus.PUBLISHED
                && lessonRepository.countBySectionCourseId(section.getCourse().getId()) <= section.getLessons().size()) {
            throw new IllegalStateException("Yayımlanmış kursun tüm dersleri silinemez");
        }
        sectionRepository.delete(section);
    }
}
