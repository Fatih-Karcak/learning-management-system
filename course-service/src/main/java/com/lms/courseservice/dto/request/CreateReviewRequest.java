package com.lms.courseservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateReviewRequest {

    @NotNull
    @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 2000)
    private String comment;
}