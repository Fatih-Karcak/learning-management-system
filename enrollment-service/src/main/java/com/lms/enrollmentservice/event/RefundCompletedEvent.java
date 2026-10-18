package com.lms.enrollmentservice.event;

import java.util.UUID;

public record RefundCompletedEvent(
        UUID paymentId,
        UUID studentId,
        UUID courseId,
        UUID refundId
) {}