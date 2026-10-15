package com.lms.paymentservice.dto.response;

import com.lms.paymentservice.domain.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PaymentResponse {
    private UUID id;
    private UUID studentId;
    private UUID courseId;
    private String courseTitle;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String receiptUrl;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}