package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.service.CourseService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/courses")
public class InternalCourseController {

    private final CourseService courses;
    private final String key;

    public InternalCourseController(CourseService courses, @Value("${internal.api-key:}") String key) {
        this.courses = courses;
        this.key = key;
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseResponse> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Internal-Service-Key", required = false) String provided
    ) {
        if (
            key.isBlank() ||
            provided == null ||
            !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
            )
        ) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Servis kimliği doğrulanamadı");
        return ApiResponse.success(courses.getInternalCourse(id), "Başarılı");
    }
}
