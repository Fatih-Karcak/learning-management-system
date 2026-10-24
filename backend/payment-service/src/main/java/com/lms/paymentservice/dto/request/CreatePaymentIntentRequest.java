package com.lms.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePaymentIntentRequest {

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    // Kupon kodu (opsiyonel)
    private String couponCode;
}