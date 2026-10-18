package com.lms.contentservice.client;

import com.lms.contentservice.dto.response.EnrollmentCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/internal/enrollments").defaultHeader(
    "X-Internal-Service-Key",
    internalApiKey
    )
public interface EnrollmentClient {
    @Value("${internal.api-key}")
    private String internalApiKey;
    @GetExchange("/course/{courseId}/check")
    EnrollmentCheckResult checkEnrollment(
            @PathVariable UUID courseId,
            @RequestParam UUID studentId);
}
