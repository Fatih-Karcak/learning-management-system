package com.lms.paymentservice.domain.enums;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELLED
}