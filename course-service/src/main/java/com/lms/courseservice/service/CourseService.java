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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseMapper courseMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CourseAccessPolicy accessPolicy;

    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request,
                                       UUID instructorId,
                                       String instructorName) {
        validatePrice(request.getPrice(), request.getDiscountPrice());
        Category category = categoryRepository.findById(request.getCategoryId())
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

        if (course.getSections().stream().noneMatch(section ->
                section.getLessons() != null && !section.getLessons().isEmpty())) {
            throw new IllegalStateException(
                    "Kurs yayınlanabilmesi için en az bir ders gerekli");
        }

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishedAt(LocalDateTime.now());
        course = courseRepository.save(course);

        // Notification Service'e bildir
        kafkaTemplate.send("course-published", course.getId().toString(),
                new CoursePublishedEvent(course.getId(), course.getTitle(),
                        course.getInstructorId()));

        log.info("Kurs yayınlandı: {}", courseId);
        return courseMapper.toResponse(course);
    }

    @Transactional
    public CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request,
                                       UUID instructorId) {
        Course course = findCourseById(courseId);
        validateInstructor(course, instructorId);

        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getShortDescription() != null)
            course.setShortDescription(request.getShortDescription());
        if (request.getPrice() != null) course.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null)
            course.setDiscountPrice(request.getDiscountPrice());
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
            response.getSections().forEach(section -> accessPolicy.filterLessons(section.getLessons(), fullAccess));
        }
        return response;
    }

    public Page<CourseSummaryResponse> getPublishedCourses(Pageable pageable) {
        return courseRepository.findByStatus(CourseStatus.PUBLISHED, pageable)
                .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryResponse> getOwnCourses(UUID instructorId, Pageable pageable) {
        return courseRepository.findByInstructorId(instructorId, pageable)
                .map(courseMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> getCategories() {
        return categoryRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .map(category -> new CategorySummary(category.getId(), category.getName(), category.getDescription()))
                .toList();
    }

    public record CategorySummary(Long id, String name, String description) {}

    public Page<CourseSummaryResponse> searchCourses(String query, Pageable pageable) {
        return courseRepository.searchPublishedCourses(query, pageable)
                .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getCoursesByCategory(
            Long categoryId, Pageable pageable) {
        return courseRepository.findByCategoryIdAndPublished(categoryId, pageable)
                .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getPopularCourses(Pageable pageable) {
        return courseRepository.findPopularCourses(pageable)
                .map(courseMapper::toSummaryResponse);
    }

    public Page<CourseSummaryResponse> getInstructorCourses(
            UUID instructorId, Pageable pageable) {
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
        if (price == null || price.signum() < 0 || discount != null
                && (discount.signum() < 0 || discount.compareTo(price) > 0)) {
            throw new IllegalArgumentException("Kurs fiyatı veya indirimli fiyat geçersiz");
        }
    }

    private Course findCourseById(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(
                        "Kurs bulunamadı: " + courseId));
    }

    private void validateInstructor(Course course, UUID instructorId) {
        if (!course.getInstructorId().equals(instructorId)) {
            throw new UnauthorizedException("Bu kursu düzenleme yetkiniz yok");
        }
    }

    public record CoursePublishedEvent(UUID courseId, String title, UUID instructorId) {}
}
