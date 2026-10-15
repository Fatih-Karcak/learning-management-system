package com.lms.enrollmentservice.event;

import java.math.BigDecimal;
import java.util.UUID;

// Payment Service'ten Kafka'ya gelecek
public record PaymentCompletedEvent(
        UUID paymentId,
        UUID studentId,
        UUID courseId,
        String courseTitle,
        String courseThumbnailUrl,
        String instructorName,
        BigDecimal amount
) {}