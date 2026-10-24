package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.LearningPathService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/paths")
@RequiredArgsConstructor
public class LearningPathController {
  private final LearningPathService service;

  @GetMapping
  public ApiResponse<?> list(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam(defaultValue = "0") int page) {
    return ApiResponse.success(service.list(user, page), "Learning paths");
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(service.detail(id, user), "Learning path");
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> create(
      @Valid @RequestBody LearningPathService.Input input,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(service.save(null, input, user), "Learning path created");
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> save(
      @PathVariable UUID id,
      @Valid @RequestBody LearningPathService.Input input,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(service.save(id, input, user), "Learning path saved");
  }
}
