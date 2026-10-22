package com.lms.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateLessonVideoRequest(@NotBlank String videoUrl, @PositiveOrZero Integer durationSeconds) {}
