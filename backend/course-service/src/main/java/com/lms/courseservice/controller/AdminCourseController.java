package com.lms.courseservice.controller;

import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService service;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(service.summary(), "Başarılı");
    }

    @GetMapping
    public ApiResponse<Page<CourseSummaryResponse>> courses(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) CourseStatus status,
        Pageable pageable
    ) {
        return ApiResponse.success(service.list(query, status, pageable), "Başarılı");
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<CourseResponse> decide(
        @PathVariable UUID id,
        @Valid @RequestBody AdminCourseService.Decision request,
        Authentication auth
    ) {
        return ApiResponse.success(
            service.decide(id, request, UUID.fromString(auth.getName())),
            "Karar kaydedildi"
        );
    }

    @PostMapping("/categories")
    public ApiResponse<CourseService.CategorySummary> addCategory(
        @Valid @RequestBody AdminCourseService.CategoryInput request
    ) {
        return ApiResponse.success(service.saveCategory(null, request), "Kategori eklendi");
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CourseService.CategorySummary> updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody AdminCourseService.CategoryInput request
    ) {
        return ApiResponse.success(service.saveCategory(id, request), "Kategori güncellendi");
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.success(null, "Kategori silindi");
    }
}
