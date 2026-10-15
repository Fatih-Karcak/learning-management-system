package com.lms.courseservice.dto.request;

import com.lms.courseservice.domain.enums.LessonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateLessonRequest {

    @NotNull
    private UUID sectionId;

    @NotBlank(message = "Ders başlığı boş olamaz")
    private String title;

    private String content;
    private String videoUrl;
    private Integer durationSeconds;
    private Integer orderIndex;
    private boolean previewAllowed = false;

    @NotNull
    private LessonType type;
}