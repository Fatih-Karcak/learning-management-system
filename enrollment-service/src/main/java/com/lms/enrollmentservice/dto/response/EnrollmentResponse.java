package com.lms.enrollmentservice.dto.response;

import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EnrollmentResponse {
    private UUID id;
    private UUID studentId;
    private UUID courseId;
    private String courseTitle;
    private String courseThumbnailUrl;
    private String instructorName;
    private BigDecimal pricePaid;
    private EnrollmentStatus status;
    private Integer totalLessons;
    private Integer completedLessons;
    private Double progressPercentage;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime completedAt;

}