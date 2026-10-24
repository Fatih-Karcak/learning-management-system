package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.LearningPolicyService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/{id}/learning-rules")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
public class LearningPolicyController {
  private final LearningPolicyService service;

  @GetMapping
  public ApiResponse<?> get(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(service.rules(id, user), "Learning rules");
  }

  @PutMapping
  public ApiResponse<?> save(
      @PathVariable UUID id,
      @Valid @RequestBody LearningPolicyService.Rules rules,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(service.save(id, rules, user), "Learning rules saved");
  }
}
