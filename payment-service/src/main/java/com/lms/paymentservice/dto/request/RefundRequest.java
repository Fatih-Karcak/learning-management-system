package com.lms.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RefundRequest {

    @NotNull
    private UUID paymentId;

    @NotBlank(message = "İade nedeni boş olamaz")
    private String reason;
}