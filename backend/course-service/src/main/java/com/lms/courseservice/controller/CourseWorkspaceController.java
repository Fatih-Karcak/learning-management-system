package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.CourseWorkspaceService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseWorkspaceController {

    private final CourseWorkspaceService service;

    @GetMapping("/{id}/workspace")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.get(id, user), "Taslak");
    }

    @PutMapping("/{id}/workspace")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> save(
        @PathVariable UUID id,
        @Valid @RequestBody CourseWorkspaceService.Save input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.save(id, input, user), "Taslak kaydedildi");
    }

    @PostMapping("/{id}/workspace/submit")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> submit(
        @PathVariable UUID id,
        @RequestParam long revision,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.submit(id, revision, user), "İncelemeye gönderildi");
    }

    @PostMapping("/{id}/workspace/withdraw")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> withdraw(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.withdraw(id, user), "Başvuru geri çekildi");
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> versions(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.history(id, user), "Sürümler");
    }

    @GetMapping("/admin/workspaces")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> pending(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.pending(user), "İncelemeler");
    }

    @PostMapping("/admin/{id}/workspace/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> decide(
        @PathVariable UUID id,
        @RequestParam long revision,
        @Valid @RequestBody CourseWorkspaceService.Decision input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.decide(id, revision, input, user), "Karar kaydedildi");
    }

    @GetMapping("/{id}/lessons/{lesson}/materials")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> materials(
        @PathVariable UUID id,
        @PathVariable UUID lesson,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.materials(id, lesson, user), "Kaynaklar");
    }
}
