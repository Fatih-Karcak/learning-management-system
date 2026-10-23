package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.dto.response.ProgressResponse;
import com.lms.enrollmentservice.service.ProgressService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/progress")
@RequiredArgsConstructor
public class InternalProgressController {

    private final ProgressService progressService;

    @PostMapping("/{enrollmentId}/lessons/{lessonId}/complete")
    public ResponseEntity<ApiResponse<ProgressResponse>>
    completeLesson(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID lessonId,
            @RequestParam UUID sectionId,
            @RequestParam UUID studentId) {

        ProgressResponse result =
                progressService.completeVerifiedLesson(
                        enrollmentId,
                        lessonId,
                        sectionId,
                        studentId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        result,
                        "Ders tamamlandı"
                )
        );
    }
}