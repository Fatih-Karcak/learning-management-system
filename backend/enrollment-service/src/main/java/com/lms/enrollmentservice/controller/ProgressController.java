package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.request.UpdateProgressRequest;
import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.dto.response.ProgressResponse;
import com.lms.enrollmentservice.service.ProgressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping("/{enrollmentId}/lesson/{lessonId}/playback")
    public ApiResponse<ProgressResponse> playback(@PathVariable UUID enrollmentId,@PathVariable UUID lessonId,
            @Valid @RequestBody ProgressService.PlaybackRequest request,Authentication auth) {
        return ApiResponse.success(progressService.recordPlayback(enrollmentId,lessonId,request,UUID.fromString(auth.getName())),"İzleme kaydedildi");
    }

    @PutMapping("/{enrollmentId}/lesson/{lessonId}")
    public ResponseEntity<ApiResponse<ProgressResponse>>
    updateProgress(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID lessonId,
            @Valid @RequestBody UpdateProgressRequest request,
            Authentication auth) {

        UUID studentId =
                UUID.fromString(auth.getName());

        ProgressResponse result =
                progressService.updateLessonProgress(
                        enrollmentId,
                        lessonId,
                        request,
                        studentId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        result,
                        "İlerleme güncellendi"
                )
        );
    }

    @GetMapping("/{enrollmentId}")
    public ResponseEntity<ApiResponse<List<ProgressResponse>>>
    getProgress(
            @PathVariable UUID enrollmentId,
            Authentication auth) {

        UUID studentId =
                UUID.fromString(auth.getName());

        return ResponseEntity.ok(
                ApiResponse.success(
                        progressService.getEnrollmentProgress(
                                enrollmentId, studentId
                        ),
                        "Başarılı"
                )
        );
    }
}
