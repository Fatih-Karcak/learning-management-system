package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Category;
import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.request.CreateCourseRequest;
import com.lms.courseservice.dto.request.UpdateCourseRequest;
import com.lms.courseservice.dto.response.CourseResponse;
import com.lms.courseservice.dto.response.CourseSummaryResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.CategoryRepository;
import com.lms.courseservice.repository.CourseRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseMapper courseMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CourseAccessPolicy accessPolicy;
    private final com.lms.courseservice.client.ContentClient contentClient;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional
    public CourseResponse createCourse(
        CreateCourseRequest request,
        UUID instructorId,
        String instructorName
    ) {
        validatePrice(request.getPrice(), request.getDiscountPrice());
        Category category = categoryRepository
            .findById(request.getCategoryId())
            .orElseThrow(() -> new IllegalArgumentException("Kategori bulunamadı"));

        Course course = Course.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .shortDescription(request.getShortDescription())
            .price(request.getPrice())
            .discountPrice(request.getDiscountPrice())
            .category(category)
            .level(request.getLevel())
            .language(request.getLanguage())
            .thumbnailUrl(request.getThumbnailUrl())
            .previewVideoUrl(request.getPreviewVideoUrl())
            .requirements(request.getRequirements())
            .outcomes(request.getOutcomes())
            .instructorId(instructorId)
            .instructorName(instructorName)
            .status(CourseStatus.DRAFT)
            .build();

        course = courseRepository.save(course);
        log.info("Kurs oluşturuldu: {} - Eğitmen: {}", course.getId(), instructorId);
        return courseMapper.toResponse(course);
    }

    @Transactional
    public CourseResponse publishCourse(UUID courseId, UUID instructorId) {
        Course course = findCourseById(courseId);
        validateInstructor(course, instructorId);

        validateCurriculum(course);

        if (
            course
                .getSections()
                .stream()
                .noneMatch(section -> section.getLessons() != null && !section.getLessons().isEmpty())
        ) {
            throw new IllegalStateException("Kurs yayınlanabilmesi için en az bir ders gerekli");
        }

        if (
            course.getStatus() == CourseStatus.PUBLISHED || course.getStatus() == CourseStatus.PENDING_REVIEW
        ) {
            return courseMapper.toResponse(course);
        }
        course.setStatus(CourseStatus.PENDING_REVIEW);
        course.setReviewFeedback(null);
        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse approveCourse(UUID courseId) {
        Course course = findCourseById(courseId);
        if (course.getStatus() != CourseStatus.PENDING_REVIEW) throw new IllegalStateException(
            "Kurs inceleme sırasında değil"
        );
        validateCurriculum(course);
        if (
            course
                .getSections()
                .stream()
                .noneMatch(s -> s.getLessons() != null && !s.getLessons().isEmpty())
        ) {
            throw new IllegalStateException("En az bir ders gerekli");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        course.setReviewFeedback(null);
        course.setPublishedAt(LocalDateTime.now());
        course = courseRepository.save(course);

        // Notification Service'e bildir
        kafkaTemplate.send(
            "course-published",
            course.getId().toString(),
            new CoursePublishedEvent(course.getId(), course.getTitle(), course.getInstructorId())
        );

        log.info("Kurs yayınlandı: {}", courseId);
        return courseMapper.toResponse(course);
    }

    @Transactional
    public CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request, UUID instructorId) {
        Course course = findCourseById(courseId);
        validateInstructor(course, instructorId);

        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getShortDescription() != null) course.setShortDescription(request.getShortDescription());
        if (request.getPrice() != null) course.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null) course.setDiscountPrice(request.getDiscountPrice());
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getLevel() != null) course.setLevel(request.getLevel());
        if (request.getRequirements() != null) course.setRequirements(request.getRequirements());
        if (request.getOutcomes() != null) course.setOutcomes(request.getOutcomes());
        validatePrice(course.getPrice(), course.getDiscountPrice());

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(UUID courseId, AuthenticatedUser viewer) {
        Course course = findCourseById(courseId);
        accessPolicy.requireVisible(course, viewer);
        CourseResponse response = courseMapper.toResponse(course);
        boolean fullAccess = accessPolicy.canReadContent(course, viewer);
        if (response.getSections() != null) {
            response
                .getSections()
                .forEach(section -> accessPolicy.filterLessons(section.getLessons(), fullAccess));
        }
        return enrichDurations(response);
    }

    @Transactional(readOnly = true)
    public CourseResponse getInternalCourse(UUID courseId) {
        return enrichDurations(courseMapper.toResponse(findCourseById(courseId)));
    }

    private UUID mediaId(String url) {
        if (url == null) return null;
        try {
            var path = java.net.URI.create(url).getPath();
            var match = java.util.regex.Pattern.compile(
                "^/api/v1/media/([0-9a-fA-F-]{36})/manifest\\.m3u8$"
            ).matcher(path == null ? "" : path);
            return match.matches() ? UUID.fromString(match.group(1)) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CourseResponse enrichDurations(CourseResponse response) {
        if (response.getSections() != null) for (var section : response.getSections())
            if (section.getLessons() != null) for (var lesson : section.getLessons()) {
                if (
                    lesson.getType() != com.lms.courseservice.domain.enums.LessonType.VIDEO ||
                    (lesson.getDurationSeconds() != null && lesson.getDurationSeconds() > 0)
                ) continue;
                UUID id = mediaId(lesson.getVideoUrl());
                if (id == null) continue;
                try {
                    var result = contentClient.metadata(id);
                    if (
                        result != null &&
                        result.success() &&
                        result.data() != null &&
                        "READY".equals(result.data().status())
                    ) lesson.setDurationSeconds(result.data().durationSeconds());
                } catch (org.springframework.web.client.RestClientException ex) {
                    log.warn("Video süresi henüz alınamadı: {}", id);
                }
            }
        return response;
    }

    private void validateCurriculum(Course course) {
        for (var section : course.getSections())
            if (section.getLessons() != null) for (var lesson : section.getLessons()) {
                if (lesson.getType() == com.lms.courseservice.domain.enums.LessonType.VIDEO) {
                    UUID id = mediaId(lesson.getVideoUrl());
                    Integer duration = lesson.getDurationSeconds();
                    if (id != null) {
                        com.lms.courseservice.client.ContentClient.Result result;
                        try {
                            result = contentClient.metadata(id);
                        } catch (org.springframework.web.client.RestClientException ex) {
                            throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                                "Video durumu doğrulanamadı; içerik servisini kontrol edin",
                                ex
                            );
                        }
                        if (
                            result == null ||
                            !result.success() ||
                            result.data() == null ||
                            !"READY".equals(result.data().status())
                        ) throw new IllegalStateException("Video işleme tamamlanmalı: " + lesson.getTitle());
                        duration = result.data().durationSeconds();
                    }
                    if (
                        lesson.getVideoUrl() == null ||
                        lesson.getVideoUrl().isBlank() ||
                        duration == null ||
                        duration <= 0
                    ) throw new IllegalStateException("Video adresi ve süresi gerekli: " + lesson.getTitle());
                }
                if (lesson.getType() == com.lms.courseservice.domain.enums.LessonType.QUIZ) {
                    Long count = jdbc.queryForObject(
                        "select count(*) from lesson_quizzes where lesson_id=?",
                        Long.class,
                        lesson.getId()
                    );
                    if (count == null || count == 0) throw new IllegalStateException(
                        "Test soruları hazırlanmalı: " + lesson.getTitle()
                    );
                }
                if (
                    (lesson.getType() == com.lms.courseservice.domain.enums.LessonType.ARTICLE ||
                        lesson.getType() == com.lms.courseservice.domain.enums.LessonType.ASSIGNMENT) &&
                    (lesson.getContent() == null || lesson.getContent().isBlank())
                ) throw new IllegalStateException("Ders içeriği gerekli: " + lesson.getTitle());
            }
    }

    @Transactional
    public CourseResponse archiveCourse(UUID courseId, UUID owner) {
        Course course = findCourseById(courseId);
        validateInstructor(course, owner);
        course.setStatus(CourseStatus.ARCHIVED);
        return courseMapper.toResponse(courseRepository.save(course));
    }

    public Page<CourseSummaryResponse> getPublishedCourses(Pageable pageable) {
        return courseRepository
            .findByStatus(CourseStatus.PUBLISHED, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getOwnCourses(UUID instructorId, Pageable pageable) {
        return courseRepository
            .findByInstructorId(instructorId, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> getCategories() {
        return categoryRepository
            .findAll(org.springframework.data.domain.Sort.by("name"))
            .stream()
            .map(category ->
                new CategorySummary(category.getId(), category.getName(), category.getDescription())
            )
            .toList();
    }

    public record CategorySummary(Long id, String name, String description) {}

    public Page<CourseSummaryResponse> searchCourses(String query, Pageable pageable) {
        return courseRepository.searchPublishedCourses(query, pageable).map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getCoursesByCategory(Long categoryId, Pageable pageable) {
        return courseRepository
            .findByCategoryIdAndPublished(categoryId, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getPopularCourses(Pageable pageable) {
        return courseRepository.findPopularCourses(pageable).map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getInstructorCourses(UUID instructorId, Pageable pageable) {
        return courseRepository
            .findByInstructorIdAndStatus(instructorId, CourseStatus.PUBLISHED, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    // Enrollment Service'ten Kafka ile çağrılır
    @Transactional
    public void incrementEnrollmentCount(UUID courseId, UUID enrollmentId) {
        if (courseId == null || enrollmentId == null) {
            throw new IllegalArgumentException("Geçersiz kayıt olayı");
        }
        if (courseRepository.claimEnrollment(enrollmentId, courseId) == 1) {
            if (courseRepository.incrementEnrollmentCount(courseId) != 1) {
                throw new CourseNotFoundException("Kurs bulunamadı");
            }
        }
    }

    private void validatePrice(java.math.BigDecimal price, java.math.BigDecimal discount) {
        if (
            price == null ||
            price.signum() < 0 ||
            (discount != null && (discount.signum() < 0 || discount.compareTo(price) > 0))
        ) {
            throw new IllegalArgumentException("Kurs fiyatı veya indirimli fiyat geçersiz");
        }
    }

    private Course findCourseById(UUID courseId) {
        return courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı: " + courseId));
    }

    private void validateInstructor(Course course, UUID instructorId) {
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu kursu düzenleme yetkiniz yok");
        }
    }

    public record CoursePublishedEvent(UUID courseId, String title, UUID instructorId) {}
}
