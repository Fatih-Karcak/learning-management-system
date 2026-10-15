package com.lms.paymentservice.client;

import com.lms.paymentservice.dto.response.CoursePriceResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/courses")
public interface CourseClient {

    @GetExchange("/{id}")
    CoursePriceResponse getCoursePrice(UUID id);
}