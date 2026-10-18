package com.lms.paymentservice.client;

import com.lms.paymentservice.dto.response.CoursePriceResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/courses")
public interface CourseClient {

    @GetExchange("/{id}")
    CoursePriceResult getCoursePrice(@PathVariable("id") UUID id);
}
