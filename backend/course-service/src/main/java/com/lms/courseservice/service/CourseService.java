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
            .orElseThrow(() ->
                new IllegalArgumentException("Category not found")
            );

        Course course = Course.builder()
            .title(request.getTitle())
            .targetAudience(request.getTargetAudience())
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
        log.info(
            "Course created: {} - Instructor: {}",
            course.getId(),
            instructorId
        );
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
                .noneMatch(
                    section ->
                        section.getLessons() != null &&
                        !section.getLessons().isEmpty()
                )
        ) {
            throw new IllegalStateException(
                "At least one lesson is required to publish a course"
            );
        }

        if (
            course.getStatus() == CourseStatus.PUBLISHED ||
            course.getStatus() == CourseStatus.PENDING_REVIEW
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
        if (
            course.getStatus() != CourseStatus.PENDING_REVIEW
        ) throw new IllegalStateException("The course is not awaiting review");
        validateCurriculum(course);
        if (
            course
                .getSections()
                .stream()
                .noneMatch(
                    s -> s.getLessons() != null && !s.getLessons().isEmpty()
                )
        ) {
            throw new IllegalStateException("At least one lesson is required");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        course.setReviewFeedback(null);
        course.setPublishedAt(LocalDateTime.now());
        course = courseRepository.save(course);

        // Notification Service'e bildir
        kafkaTemplate.send(
            "course-published",
            course.getId().toString(),
            new CoursePublishedEvent(
                course.getId(),
                course.getTitle(),
                course.getInstructorId()
            )
        );

        log.info("Course published: {}", courseId);
        return courseMapper.toResponse(course);
    }

    @Transactional
    public CourseResponse updateCourse(
        UUID courseId,
        UpdateCourseRequest request,
        UUID instructorId
    ) {
        Course course = findCourseById(courseId);
        validateInstructor(course, instructorId);
        CourseWorkspaceService.requireDirectEditable(course);

        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getTargetAudience() != null) course.setTargetAudience(
            request.getTargetAudience()
        );
        if (request.getDescription() != null) course.setDescription(
            request.getDescription()
        );
        if (request.getShortDescription() != null) course.setShortDescription(
            request.getShortDescription()
        );
        if (request.getPrice() != null) course.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null) course.setDiscountPrice(
            request.getDiscountPrice()
        );
        if (request.getThumbnailUrl() != null) course.setThumbnailUrl(
            request.getThumbnailUrl()
        );
        if (request.getLevel() != null) course.setLevel(request.getLevel());
        if (request.getRequirements() != null) course.setRequirements(
            request.getRequirements()
        );
        if (request.getOutcomes() != null) course.setOutcomes(
            request.getOutcomes()
        );
        validatePrice(course.getPrice(), course.getDiscountPrice());

        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(
        UUID courseId,
        AuthenticatedUser viewer
    ) {
        Course course = findCourseById(courseId);
        accessPolicy.requireVisible(course, viewer);
        CourseResponse response = courseMapper.toResponse(course);
        boolean fullAccess = accessPolicy.canReadContent(course, viewer);
        if (response.getSections() != null) {
            response
                .getSections()
                .forEach(section ->
                    accessPolicy.filterCourseLessons(course, section.getLessons(), fullAccess, viewer)
                );
        }
        return enrichDurations(response);
    }

    @Transactional(readOnly = true)
    public CourseResponse getInternalCourse(UUID courseId) {
        return enrichDurations(
            courseMapper.toResponse(findCourseById(courseId))
        );
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
        if (
            response.getSections() != null
        ) for (var section : response.getSections())
            if (
                section.getLessons() != null
            ) for (var lesson : section.getLessons()) {
                if (
                    lesson.getType() !=
                        com.lms.courseservice.domain.enums.LessonType.VIDEO ||
                    (lesson.getDurationSeconds() != null &&
                        lesson.getDurationSeconds() > 0)
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
                    ) lesson.setDurationSeconds(
                        result.data().durationSeconds()
                    );
                } catch (
                    org.springframework.web.client.RestClientException ex
                ) {
                    log.warn("Video duration is not available yet: {}", id);
                }
            }
        return response;
    }

    private void validateCurriculum(Course course) {
        for (var section : course.getSections())
            if (
                section.getLessons() != null
            ) for (var lesson : section.getLessons()) {
                if (
                    lesson.getType() ==
                    com.lms.courseservice.domain.enums.LessonType.VIDEO
                ) {
                    UUID id = mediaId(lesson.getVideoUrl());
                    Integer duration = lesson.getDurationSeconds();
                    if (id != null) {
                        com.lms.courseservice.client.ContentClient.Result result;
                        try {
                            result = contentClient.metadata(id);
                        } catch (
                            org.springframework.web.client.RestClientException ex
                        ) {
                            throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                                "Video status could not be verified; check the content service",
                                ex
                            );
                        }
                        if (
                            result == null ||
                            !result.success() ||
                            result.data() == null ||
                            !"READY".equals(result.data().status())
                        ) throw new IllegalStateException(
                            "Video processing must finish: " + lesson.getTitle()
                        );
                        duration = result.data().durationSeconds();
                    }
                    if (
                        lesson.getVideoUrl() == null ||
                        lesson.getVideoUrl().isBlank() ||
                        duration == null ||
                        duration <= 0
                    ) throw new IllegalStateException(
                        "A video URL and duration are required: " + lesson.getTitle()
                    );
                }
                if (
                    lesson.getType() ==
                    com.lms.courseservice.domain.enums.LessonType.QUIZ
                ) {
                    Long count = jdbc.queryForObject(
                        "select count(*) from lesson_quizzes where lesson_id=?",
                        Long.class,
                        lesson.getId()
                    );
                    if (
                        count == null || count == 0
                    ) throw new IllegalStateException(
                        "Prepare the quiz questions: " + lesson.getTitle()
                    );
                }
                if (
                    (lesson.getType() ==
                        com.lms.courseservice.domain.enums.LessonType.ARTICLE ||
                        lesson.getType() ==
                            com.lms.courseservice.domain.enums.LessonType.ASSIGNMENT) &&
                    (lesson.getContent() == null ||
                        lesson.getContent().isBlank())
                ) throw new IllegalStateException(
                    "Lesson content is required: " + lesson.getTitle()
                );
            }
    }

    @Transactional
    public CourseResponse archiveCourse(UUID courseId, UUID owner) {
        Course course = findCourseById(courseId);
        validateInstructor(course, owner);
        course.setStatus(CourseStatus.ARCHIVED);
        jdbc.update(
            "update course_workspaces set status='DRAFT',revision=revision+1 where course_id=? and status='PENDING_REVIEW'",
            courseId
        );
        return courseMapper.toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> discover(
        String query,
        Long category,
        com.lms.courseservice.domain.enums.CourseLevel level,
        String language,
        boolean free,
        Double rating,
        String order,
        Pageable pageable
    ) {
        if (
            query.length() > 200 ||
            (language != null && language.length() > 50) ||
            (rating != null && (rating < 0 || rating > 5))
        ) throw new IllegalArgumentException("Invalid filter");
        var sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC,
            switch (order) {
                case "popular" -> "totalEnrollments";
                case "rating" -> "averageRating";
                default -> "createdAt";
            }
        ).and(org.springframework.data.domain.Sort.by("id"));
        var page = org.springframework.data.domain.PageRequest.of(
            pageable.getPageNumber(),
            Math.min(100, pageable.getPageSize()),
            sort
        );
        return courseRepository
            .findAll((root, q, cb) -> {
                var p =
                    new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                p.add(cb.equal(root.get("status"), CourseStatus.PUBLISHED));
                if (!query.isBlank()) {
                    String term =
                        "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
                    p.add(
                        cb.or(
                            cb.like(cb.lower(root.get("title")), term),
                            cb.like(cb.lower(root.get("description")), term),
                            cb.like(cb.lower(root.get("instructorName")), term)
                        )
                    );
                }
                if (category != null) p.add(
                    cb.equal(root.get("category").get("id"), category)
                );
                if (level != null) p.add(cb.equal(root.get("level"), level));
                if (language != null && !language.isBlank()) p.add(
                    cb.equal(
                        cb.lower(root.get("language")),
                        language.toLowerCase(java.util.Locale.ROOT)
                    )
                );
                if (free) p.add(
                    cb.equal(
                        cb.coalesce(
                            root.get("discountPrice"),
                            root.get("price")
                        ),
                        java.math.BigDecimal.ZERO
                    )
                );
                if (rating != null) p.add(
                    cb.ge(root.get("averageRating"), rating)
                );
                return cb.and(
                    p.toArray(jakarta.persistence.criteria.Predicate[]::new)
                );
            }, page)
            .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getPublishedCourses(Pageable pageable) {
        return courseRepository
            .findByStatus(CourseStatus.PUBLISHED, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getOwnCourses(
        UUID instructorId,
        Pageable pageable
    ) {
        var shared = jdbc.queryForList(
            "select course_id from course_collaborators where user_id=? and status='ACCEPTED'",
            UUID.class,
            instructorId
        );
        if (shared.isEmpty()) return courseRepository
            .findByInstructorId(instructorId, pageable)
            .map(courseMapper::toSummaryResponse);
        return courseRepository
            .findAll(
                (root, query, cb) ->
                    shared.isEmpty()
                        ? cb.equal(root.get("instructorId"), instructorId)
                        : cb.or(
                              cb.equal(root.get("instructorId"), instructorId),
                              root.get("id").in(shared)
                          ),
                pageable
            )
            .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> getCategories() {
        return categoryRepository
            .findAll(org.springframework.data.domain.Sort.by("name"))
            .stream()
            .map(category ->
                new CategorySummary(
                    category.getId(),
                    category.getName(),
                    category.getDescription()
                )
            )
            .toList();
    }

    public record CategorySummary(Long id, String name, String description) {}

    public Page<CourseSummaryResponse> searchCourses(
        String query,
        Pageable pageable
    ) {
        return courseRepository
            .searchPublishedCourses(query, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getCoursesByCategory(
        Long categoryId,
        Pageable pageable
    ) {
        return courseRepository
            .findByCategoryIdAndPublished(categoryId, pageable)
            .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getPopularCourses(Pageable pageable) {
        return courseRepository
            .findPopularCourses(pageable)
            .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getInstructorCourses(
        UUID instructorId,
        Pageable pageable
    ) {
        return courseRepository
            .findByInstructorIdAndStatus(
                instructorId,
                CourseStatus.PUBLISHED,
                pageable
            )
            .map(courseMapper::toSummaryResponse);
    }

    // Enrollment Service'ten Kafka ile çağrılır
    @Transactional
    public void incrementEnrollmentCount(UUID courseId, UUID enrollmentId) {
        if (courseId == null || enrollmentId == null) {
            throw new IllegalArgumentException("Invalid enrollment event");
        }
        if (courseRepository.claimEnrollment(enrollmentId, courseId) == 1) {
            if (courseRepository.incrementEnrollmentCount(courseId) != 1) {
                throw new CourseNotFoundException("Course not found");
            }
        }
    }

    private void validatePrice(
        java.math.BigDecimal price,
        java.math.BigDecimal discount
    ) {
        if (
            price == null ||
            price.signum() < 0 ||
            (discount != null &&
                (discount.signum() < 0 || discount.compareTo(price) > 0))
        ) {
            throw new IllegalArgumentException(
                "Invalid course price or discounted price"
            );
        }
    }

    private Course findCourseById(UUID courseId) {
        return courseRepository
            .findById(courseId)
            .orElseThrow(() ->
                new CourseNotFoundException("Course not found: " + courseId)
            );
    }

    private void validateInstructor(Course course, UUID instructorId) {
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("You do not have permission to edit this course");
        }
    }

    public record CoursePublishedEvent(
        UUID courseId,
        String title,
        UUID instructorId
    ) {}
}
