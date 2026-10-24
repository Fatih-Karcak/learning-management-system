package com.lms.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RefundRequest {

    @NotNull
    private UUID paymentId;

    @NotBlank(message = "A refund reason is required")
    private String reason;
}