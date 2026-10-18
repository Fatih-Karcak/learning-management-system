package com.lms.courseservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import java.util.UUID;

public interface EnrollmentClient {
    @GetExchange("/api/v1/internal/enrollments/course/{courseId}/check")
    Result check(@PathVariable UUID courseId, @RequestParam UUID studentId);

    record Result(boolean success, Boolean data) {}
}
