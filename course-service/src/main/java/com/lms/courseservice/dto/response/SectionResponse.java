package com.lms.courseservice.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SectionResponse {
    private UUID id;
    private String title;
    private String description;
    private Integer orderIndex;
    private List<LessonResponse> lessons;
}