package com.lms.paymentservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID studentId,
        UUID courseId,
        String courseTitle,
        String courseThumbnailUrl,
        String instructorName,
        BigDecimal amount
) {}