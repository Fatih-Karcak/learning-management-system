package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.AssessmentService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/lessons/{lessonId}")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService service;

    @GetMapping("/quiz")
    public ApiResponse<AssessmentService.PublicQuiz> quiz(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.view(courseId, lessonId, user), "Başarılı");
    }

    @GetMapping("/quiz/manage")
    public ApiResponse<AssessmentService.Quiz> manage(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.manage(courseId, lessonId, user), "Başarılı");
    }

    @PutMapping("/quiz/manage")
    public ApiResponse<AssessmentService.Quiz> save(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.Quiz request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.save(courseId, lessonId, request, user), "Test kaydedildi");
    }

    @PostMapping("/quiz/start")
    public ApiResponse<?> start(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.StartInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.start(courseId, lessonId, request, user), "Test başladı");
    }

    @GetMapping("/assignment/history")
    public ApiResponse<?> history(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.assignmentHistory(courseId, lessonId, user), "Teslim geçmişi");
    }

    @PostMapping("/quiz/attempts")
    public ApiResponse<AssessmentService.Attempt> attempt(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.AttemptInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
            service.attempt(courseId, lessonId, request, user),
            "Test değerlendirildi"
        );
    }

    @GetMapping("/quiz/attempts")
    public ApiResponse<List<Map<String, Object>>> attempts(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.attempts(courseId, lessonId, user), "Başarılı");
    }

    @GetMapping("/assignment/my")
    public ApiResponse<AssessmentService.Submission> mine(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.mine(courseId, lessonId, user), "Başarılı");
    }

    @PostMapping("/assignment")
    public ApiResponse<AssessmentService.Submission> submit(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.SubmissionInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.submit(courseId, lessonId, request, user), "Ödev gönderildi");
    }

    @GetMapping("/assignment/submissions")
    public ApiResponse<Page<AssessmentService.Submission>> submissions(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user,
        Pageable pageable
    ) {
        return ApiResponse.success(service.submissions(courseId, lessonId, user, pageable), "Başarılı");
    }

    @PutMapping("/assignment/submissions/{submissionId}")
    public ApiResponse<AssessmentService.Submission> grade(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @PathVariable UUID submissionId,
        @Valid @RequestBody AssessmentService.Grade request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
            service.grade(courseId, lessonId, submissionId, request, user),
            "Ödev değerlendirildi"
        );
    }
}
