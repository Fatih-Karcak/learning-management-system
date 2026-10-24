package com.lms.enrollmentservice.client;

import com.lms.enrollmentservice.dto.response.CourseEnvelope;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/internal/courses")
public interface CourseClient {
    @GetExchange("/{id}/lessons/{lesson}/access")
    Access access(@PathVariable UUID id,@PathVariable UUID lesson,@org.springframework.web.bind.annotation.RequestParam UUID studentId);
    record Access(boolean success,Boolean data) {}

    @GetExchange("/{id}")
    CourseEnvelope getCourseById(
            @PathVariable("id") UUID courseId
    );
}
