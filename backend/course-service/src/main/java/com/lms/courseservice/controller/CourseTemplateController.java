package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.CourseTemplateService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
public class CourseTemplateController {

    private final CourseTemplateService service;

    @GetMapping("/templates")
    public ApiResponse<?> list(
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.list(user), "Şablonlar");
    }

    @PostMapping("/{id}/templates")
    public ApiResponse<?> save(
        @PathVariable UUID id,
        @Valid @RequestBody CourseTemplateService.Save input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
            service.save(id, input, user),
            "Kişisel şablon kaydedildi"
        );
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<?> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.delete(id, user);
        return ApiResponse.success(null, "Şablon silindi");
    }

    @PostMapping("/templates/{id}/create")
    public ApiResponse<?> create(
        @PathVariable String id,
        @Valid @RequestBody CourseTemplateService.Create input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
            service.create(id, input, user),
            "Şablondan taslak oluşturuldu"
        );
    }
}
