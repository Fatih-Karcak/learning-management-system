package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.service.LearningToolsService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class LearningToolsController {

    private final LearningToolsService service;

    private UUID id(Authentication a) {
        return UUID.fromString(a.getName());
    }

    public record Done(boolean done) {}

    @GetMapping("/learning/dashboard")
    public ApiResponse<?> dashboard(Authentication a) {
        return ApiResponse.success(service.dashboard(id(a)), "Workspace");
    }

    @GetMapping("/learning/export")
    public ApiResponse<?> export(Authentication a) {
        return ApiResponse.success(service.export(id(a)), "Personal learning data");
    }

    @PostMapping("/learning/tasks")
    public ApiResponse<?> task(Authentication a, @Valid @RequestBody LearningToolsService.Task t) {
        service.createTask(id(a), t);
        return ApiResponse.success(null, "Task added");
    }

    @PutMapping("/learning/tasks/{task}")
    public ApiResponse<?> task(Authentication a, @PathVariable UUID task, @RequestBody Done d) {
        service.task(id(a), task, d.done());
        return ApiResponse.success(null, "Plan updated");
    }

    @DeleteMapping("/learning/tasks/{task}")
    public ApiResponse<?> deleteTask(Authentication a, @PathVariable UUID task) {
        service.deleteTask(id(a), task);
        return ApiResponse.success(null, "Task deleted");
    }

    @GetMapping("/{enrollment}/bookmarks")
    public ApiResponse<?> bookmarks(Authentication a, @PathVariable UUID enrollment) {
        return ApiResponse.success(service.bookmarks(enrollment, id(a)), "Bookmarks");
    }

    @PostMapping("/{enrollment}/bookmarks")
    public ApiResponse<?> bookmark(
        Authentication a,
        @PathVariable UUID enrollment,
        @Valid @RequestBody LearningToolsService.Bookmark b
    ) {
        service.bookmark(enrollment, id(a), b);
        return ApiResponse.success(null, "Bookmark added");
    }

    @DeleteMapping("/{enrollment}/bookmarks/{bookmark}")
    public ApiResponse<?> deleteBookmark(
        Authentication a,
        @PathVariable UUID enrollment,
        @PathVariable UUID bookmark
    ) {
        service.deleteBookmark(enrollment, id(a), bookmark);
        return ApiResponse.success(null, "Bookmark deleted");
    }

    @GetMapping("/courses/{course}/analytics")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> analytics(Authentication a, @PathVariable UUID course) {
        return ApiResponse.success(
            service.analytics(
                course,
                id(a),
                a
                    .getAuthorities()
                    .stream()
                    .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"))
            ),
            "Learning report"
        );
    }
}
