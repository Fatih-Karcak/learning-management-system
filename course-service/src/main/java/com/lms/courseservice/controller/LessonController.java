package com.lms.courseservice.controller;

import com.lms.courseservice.dto.request.CreateLessonRequest;
import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.service.LessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LessonResponse>> createLesson(
            @Valid @RequestBody CreateLessonRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        lessonService.createLesson(request, instructorId), "Ders oluşturuldu"));
    }

    @GetMapping("/section/{sectionId}")
    public ResponseEntity<ApiResponse<List<LessonResponse>>> getLessons(
            @PathVariable UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success(
                lessonService.getLessonsBySection(sectionId), "Başarılı"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID instructorId) {
        lessonService.deleteLesson(id, instructorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Ders silindi"));
    }
}