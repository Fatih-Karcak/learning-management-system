package com.lms.courseservice.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReviewResponse {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private String studentAvatarUrl;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}