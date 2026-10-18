package com.lms.enrollmentservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateProgressRequest {

    @NotNull
    private UUID sectionId;

    @Min(0)
    private Integer watchedSeconds;

    // Geriye dönük istemci uyumluluğu.
    // Gerçek ders süresi sunucudan alınacak.
    @Min(0)
    private Integer totalSeconds;

    private Boolean completed;
}