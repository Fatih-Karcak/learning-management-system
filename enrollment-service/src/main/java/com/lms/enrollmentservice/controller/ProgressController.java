package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.dto.response.ProgressResponse;
import com.lms.enrollmentservice.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PutMapping("/{enrollmentId}/lesson/{lessonId}")
    public ResponseEntity<ApiResponse<ProgressResponse>> updateProgress(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID lessonId,
            @RequestBody UpdateProgressRequest request,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.updateLessonProgress(
                        enrollmentId, lessonId, request, studentId),
                "İlerleme güncellendi"));
    }

    @GetMapping("/{enrollmentId}")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>> getProgress(
            @PathVariable UUID enrollmentId,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getEnrollmentProgress(enrollmentId, studentId),
                "Başarılı"));
    }
}