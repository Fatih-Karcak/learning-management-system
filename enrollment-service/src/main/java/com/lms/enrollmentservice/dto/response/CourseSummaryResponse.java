package com.lms.enrollmentservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        String title,
        String thumbnailUrl,
        String instructorName,
        BigDecimal price,
        Integer totalLessons
) {}