package com.lms.paymentservice.dto.response;

import com.lms.paymentservice.domain.enums.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RefundResponse {
    private UUID id;
    private UUID paymentId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private LocalDateTime createdAt;
}