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

    public List<SectionResponse> getSectionsByCourse(UUID courseId) {
        return sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream()
                .map(courseMapper::toSectionResponse)
                .toList();
    }

    @Transactional
    public void deleteSection(UUID sectionId, UUID instructorId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CourseNotFoundException("Bölüm bulunamadı"));

        if (!section.getCourse().getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu bölümü silme yetkiniz yok");
        }

        sectionRepository.delete(section);
    }
}