package com.lms.courseservice.controller;

import com.lms.courseservice.dto.request.CreateCourseRequest;
import com.lms.courseservice.dto.request.UpdateCourseRequest;
import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.dto.response.CourseResponse;
import com.lms.courseservice.dto.response.CourseSummaryResponse;
import com.lms.courseservice.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.lms.courseservice.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getOwnCourses(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getOwnCourses(user.id(), pageable), "Başarılı"));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<java.util.List<CourseService.CategorySummary>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(courseService.getCategories(), "Başarılı"));
    }

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        courseService.createCourse(request, user.id(), user.displayName()),
                        "Kurs oluşturuldu"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.updateCourse(id, request, user.id()), "Kurs güncellendi"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.publishCourse(id, user.id()), "Kurs yayınlandı"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getCourseById(id, viewer), "Başarılı"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getPublishedCourses(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getPublishedCourses(pageable), "Başarılı"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> searchCourses(
            @RequestParam String query, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.searchCourses(query, pageable), "Başarılı"));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getCoursesByCategory(
            @PathVariable Long categoryId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getCoursesByCategory(categoryId, pageable), "Başarılı"));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getPopularCourses(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getPopularCourses(pageable), "Başarılı"));
    }

    @GetMapping("/instructor/{instructorId}")
    public ResponseEntity<ApiResponse<Page<CourseSummaryResponse>>> getInstructorCourses(
            @PathVariable UUID instructorId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getInstructorCourses(instructorId, pageable), "Başarılı"));
    }
}
