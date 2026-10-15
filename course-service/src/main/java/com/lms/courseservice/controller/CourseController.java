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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @RequestHeader("X-User-Id") UUID instructorId,
            @RequestHeader("X-User-Name") String instructorName) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        courseService.createCourse(request, instructorId, instructorName),
                        "Kurs oluşturuldu"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.updateCourse(id, request, instructorId), "Kurs güncellendi"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> publishCourse(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.publishCourse(id, instructorId), "Kurs yayınlandı"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.getCourseById(id), "Başarılı"));
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