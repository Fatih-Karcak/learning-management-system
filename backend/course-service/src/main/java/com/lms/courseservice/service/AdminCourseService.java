package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Category;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.*;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final CourseMapper mapper;
    private final CourseService courseService;
    private final JdbcTemplate jdbc;

    public enum Action {
        APPROVE,
        REJECT,
        ARCHIVE,
    }

    public record Decision(@NotNull Action action, @Size(max = 2000) String reason) {}

    public record CategoryInput(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 5000) String description
    ) {}

    public Map<String, Object> summary() {
        return jdbc.queryForMap(
            "select count(*) as total,count(*) filter(where status='PUBLISHED') as published," +
                "count(*) filter(where status='PENDING_REVIEW') as pending,count(*) filter(where status='ARCHIVED') as archived from courses"
        );
    }

    public Page<CourseSummaryResponse> list(String query, CourseStatus status, Pageable pageable) {
        return courses.adminSearch(query, status, pageable).map(mapper::toSummaryResponse);
    }

    @Transactional
    public CourseResponse decide(UUID id, Decision request, UUID actor) {
        var course = courses.findById(id).orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı"));
        if (
            request.action() != Action.APPROVE && (request.reason() == null || request.reason().isBlank())
        ) throw new IllegalArgumentException("Karar gerekçesi gerekli");
        if (request.action() == Action.APPROVE) courseService.approveCourse(id);
        else {
            if (
                request.action() == Action.REJECT && course.getStatus() != CourseStatus.PENDING_REVIEW
            ) throw new IllegalStateException("Kurs inceleme sırasında değil");
            course.setStatus(request.action() == Action.REJECT ? CourseStatus.DRAFT : CourseStatus.ARCHIVED);
            course.setReviewFeedback(request.reason().trim());
            courses.save(course);
        }
        jdbc.update(
            "insert into course_review_events(id,course_id,actor_id,decision,reason) values(?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            actor,
            request.action().name(),
            request.reason()
        );
        return mapper.toResponse(course);
    }

    @Transactional
    public CourseService.CategorySummary saveCategory(Long id, CategoryInput request) {
        var category =
            id == null
                ? new Category()
                : categories
                      .findById(id)
                      .orElseThrow(() -> new CourseNotFoundException("Kategori bulunamadı"));
        if (
            categories
                .findAll()
                .stream()
                .anyMatch(
                    c -> c.getName().equalsIgnoreCase(request.name().trim()) && !Objects.equals(c.getId(), id)
                )
        ) throw new IllegalStateException("Bu kategori adı zaten var");
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category = categories.save(category);
        return new CourseService.CategorySummary(
            category.getId(),
            category.getName(),
            category.getDescription()
        );
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (courses.existsByCategoryId(id)) throw new IllegalStateException("Kurs içeren kategori silinemez");
        var category = categories
            .findById(id)
            .orElseThrow(() -> new CourseNotFoundException("Kategori bulunamadı"));
        if (
            category.getSubCategories() != null && !category.getSubCategories().isEmpty()
        ) throw new IllegalStateException("Alt kategorileri olan kategori silinemez");
        categories.delete(category);
    }
}
