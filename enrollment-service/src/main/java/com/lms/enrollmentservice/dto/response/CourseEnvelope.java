package com.lms.enrollmentservice.dto.response;

public record CourseEnvelope(
        boolean success,
        String message,
        CourseSummaryResponse data
) {}