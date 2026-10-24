package com.lms.courseservice.controller;

import com.lms.courseservice.dto.request.CreateCourseRequest;
import com.lms.courseservice.dto.request.UpdateCourseRequest;
import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.dto.response.CourseResponse;
import com.lms.courseservice.dto.response.CourseSummaryResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.CourseService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/discover")
    public ApiResponse<Page<CourseSummaryResponse>> discover(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) Long category,
        @RequestParam(required = false) com.lms.courseservice.domain.enums.CourseLevel level,
        @RequestParam(required = false) String language,
        @RequestParam(defaultValue = "false") boolean free,
        @RequestParam(required = false) Double rating,
        @RequestParam(defaultValue = "new") String order,
        Pageable pageable
    ) {
        return ApiResponse.success(
            courseService.discover(query, category, level, language, free, rating, order, pageable),
            "Courses"
        );
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getOwnCourses(
        @AuthenticationPrincipal AuthenticatedUser user,
        Pageable pageable
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.getOwnCourses(user.id(), pageable), "Successful")
        );
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<java.util.List<CourseService.CategorySummary>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(courseService.getCategories(), "Successful"));
    }

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
        @Valid @RequestBody CreateCourseRequest request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                courseService.createCourse(request, user.id(), user.displayName()),
                "Course created"
            )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCourseRequest request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.updateCourse(id, request, user.id()), "Course updated")
        );
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(
                courseService.publishCourse(id, user.id()),
                "Course submitted for administrator review"
            )
        );
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.archiveCourse(id, user.id()), "Course archived")
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return ResponseEntity.ok(ApiResponse.success(courseService.getCourseById(id, viewer), "Successful"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getPublishedCourses(Pageable pageable) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.getPublishedCourses(pageable), "Successful")
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> searchCourses(
        @RequestParam String query,
        Pageable pageable
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.searchCourses(query, pageable), "Successful")
        );
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getCoursesByCategory(
        @PathVariable Long categoryId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.getCoursesByCategory(categoryId, pageable), "Successful")
        );
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getPopularCourses(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(courseService.getPopularCourses(pageable), "Successful"));
    }

    @GetMapping("/instructor/{instructorId}")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getInstructorCourses(
        @PathVariable UUID instructorId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(courseService.getInstructorCourses(instructorId, pageable), "Successful")
        );
    }
}
