package com.lms.courseservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSectionRequest {

    @NotNull
    private UUID courseId;

    @NotBlank(message = "Section title is required")
    private String title;

    private String description;
    private Integer orderIndex;

}