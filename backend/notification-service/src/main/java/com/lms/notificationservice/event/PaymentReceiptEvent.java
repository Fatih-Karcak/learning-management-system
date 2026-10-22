package com.lms.notificationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentReceiptEvent(
UUID paymentId,
UUID studentId,
UUID courseId,
String courseTitle,
String courseThumbnailUrl,
String instructorName,
BigDecimal amount
) {
}
