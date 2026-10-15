package com.lms.courseservice.dto.response;

import com.lms.courseservice.domain.enums.LessonType;
import lombok.Data;

import java.util.UUID;

@Data
public class LessonResponse {
    private UUID id;
    private String title;
    private String content;
    private String videoUrl;
    private Integer durationSeconds;
    private LessonType type;
    private Integer orderIndex;
    private boolean previewAllowed;
}