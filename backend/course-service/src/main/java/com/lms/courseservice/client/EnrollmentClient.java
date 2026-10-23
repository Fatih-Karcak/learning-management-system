package com.lms.courseservice.client;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface EnrollmentClient {
    @GetExchange("/api/v1/internal/enrollments/{enrollmentId}/verify")
    Result verify(
        @org.springframework.web.bind.annotation.PathVariable UUID enrollmentId,
        @org.springframework.web.bind.annotation.RequestParam UUID courseId,
        @org.springframework.web.bind.annotation.RequestParam UUID studentId
    );

    @org.springframework.web.service.annotation.PostExchange(
        "/api/v1/internal/progress/{enrollmentId}/lessons/{lessonId}/complete"
    )
    Completion complete(
        @org.springframework.web.bind.annotation.PathVariable UUID enrollmentId,
        @org.springframework.web.bind.annotation.PathVariable UUID lessonId,
        @org.springframework.web.bind.annotation.RequestParam UUID sectionId,
        @org.springframework.web.bind.annotation.RequestParam UUID studentId
    );

    record Completion(boolean success, String message) {}

    @GetExchange("/api/v1/internal/enrollments/course/{courseId}/check")
    Result check(@PathVariable UUID courseId, @RequestParam UUID studentId);

    record Result(boolean success, Boolean data) {}
}
