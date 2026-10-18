package com.lms.contentservice.client;

import com.lms.contentservice.dto.response.EnrollmentCheckResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/internal/enrollments")
public interface EnrollmentClient {
    @GetExchange("/course/{courseId}/check")
    EnrollmentCheckResult checkEnrollment(
            @PathVariable UUID courseId,
            @RequestParam UUID studentId);
}
