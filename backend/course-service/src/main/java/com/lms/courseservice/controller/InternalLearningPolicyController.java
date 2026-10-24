package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.service.LearningPolicyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/courses")
public class InternalLearningPolicyController {
  private final LearningPolicyService service;
  private final String key;

  public InternalLearningPolicyController(
      LearningPolicyService service, @Value("${internal.api-key:}") String key) {
    this.service = service;
    this.key = key;
  }

  @GetMapping("/{id}/lessons/{lesson}/access")
  public ApiResponse<Boolean> access(
      @PathVariable UUID id,
      @PathVariable UUID lesson,
      @RequestParam UUID studentId,
      @RequestHeader(value = "X-Internal-Service-Key", required = false) String provided) {
    if (key.isBlank()
        || provided == null
        || !MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service authentication required");
    return ApiResponse.success(service.canAccess(id, lesson, studentId), "Learning access");
  }

  @GetMapping("/{id}/resources/{resource}/access")
  public ApiResponse<Boolean> resource(
      @PathVariable UUID id,
      @PathVariable UUID resource,
      @RequestParam UUID studentId,
      @RequestHeader(value = "X-Internal-Service-Key", required = false) String provided) {
    if (key.isBlank()
        || provided == null
        || !MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service authentication required");
    return ApiResponse.success(service.canReadResource(id, resource, studentId), "Resource access");
  }
}
