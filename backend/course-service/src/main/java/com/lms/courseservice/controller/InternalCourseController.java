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
    private final com.lms.courseservice.service.CourseAuthoringAccess authors;

    public InternalCourseController(
        CourseService courses,
        @Value("${internal.api-key:}") String key,
        com.lms.courseservice.service.CourseAuthoringAccess authors
    ) {
        this.courses = courses;
        this.key = key;
        this.authors = authors;
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseResponse> get(
        @PathVariable UUID id,
        @RequestHeader(
            value = "X-Internal-Service-Key",
            required = false
        ) String provided
    ) {
        requireKey(provided);
        return ApiResponse.success(courses.getInternalCourse(id), "Başarılı");
    }

    @GetMapping("/{id}/authors/{user}")
    public ApiResponse<Boolean> author(
        @PathVariable UUID id,
        @PathVariable UUID user,
        @RequestHeader(
            value = "X-Internal-Service-Key",
            required = false
        ) String provided
    ) {
        requireKey(provided);
        return ApiResponse.success(
            authors.canEdit(id, user),
            "Düzenleme yetkisi"
        );
    }

    private void requireKey(String provided) {
        if (
            key.isBlank() ||
            provided == null ||
            !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
            )
        ) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Servis kimliği doğrulanamadı"
        );
    }
}
