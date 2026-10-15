package com.lms.enrollmentservice.dto.response;

import com.lms.enrollmentservice.domain.enums.CompletionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProgressResponse {
    private UUID enrollmentId;
    private UUID lessonId;
    private UUID sectionId;
    private CompletionStatus status;
    private Integer watchedSeconds;
    private Integer totalSeconds;
    private LocalDateTime completedAt;
}