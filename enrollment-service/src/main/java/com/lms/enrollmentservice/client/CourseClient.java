package com.lms.enrollmentservice.client;

import com.lms.enrollmentservice.dto.response.CourseSummaryResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/courses")
public interface CourseClient {

    @GetExchange("/{id}")
    CourseSummaryResponse getCourseById(UUID id);
}