package com.lms.enrollmentservice.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateProgressRequest {
    private UUID sectionId;
    private Integer watchedSeconds;
    private Integer totalSeconds;
    private Boolean completed;
}