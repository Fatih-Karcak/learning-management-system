package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.service.LearningReminderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments/learning/reminders")
@RequiredArgsConstructor
public class LearningReminderController {
  private final LearningReminderService service;

  @GetMapping
  public ApiResponse<?> get(Authentication user) {
    return ApiResponse.success(
        service.get(UUID.fromString(user.getName())), "Reminder preferences");
  }

  @PutMapping
  public ApiResponse<?> save(
      Authentication user, @Valid @RequestBody LearningReminderService.Preferences input) {
    return ApiResponse.success(
        service.save(UUID.fromString(user.getName()), input), "Reminder preferences saved");
  }
}
