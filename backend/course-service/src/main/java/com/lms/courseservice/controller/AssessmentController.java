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

    @GetMapping("/assignment/policy")
    public ApiResponse<?> policy(@PathVariable UUID courseId,@PathVariable UUID lessonId,@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.assignmentPolicy(courseId,lessonId,user),"Assignment policy");
    }
    @PutMapping("/assignment/policy")
    public ApiResponse<?> savePolicy(@PathVariable UUID courseId,@PathVariable UUID lessonId,
        @Valid @RequestBody com.lms.courseservice.service.AssignmentPolicyService.Policy input,@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.saveAssignmentPolicy(courseId,lessonId,input,user),"Assignment policy saved");
    }

    @GetMapping("/quiz")
    public ApiResponse<AssessmentService.PublicQuiz> quiz(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.view(courseId, lessonId, user), "Successful");
    }

    @GetMapping("/quiz/manage")
    public ApiResponse<AssessmentService.Quiz> manage(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.manage(courseId, lessonId, user), "Successful");
    }

    @PutMapping("/quiz/manage")
    public ApiResponse<AssessmentService.Quiz> save(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.Quiz request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.save(courseId, lessonId, request, user), "Quiz saved");
    }

    @PostMapping("/quiz/start")
    public ApiResponse<?> start(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.StartInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.start(courseId, lessonId, request, user), "Quiz started");
    }

    @GetMapping("/assignment/history")
    public ApiResponse<?> history(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.assignmentHistory(courseId, lessonId, user), "Submission history");
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
            "Quiz graded"
        );
    }

    @GetMapping("/quiz/attempts")
    public ApiResponse<List<Map<String, Object>>> attempts(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.attempts(courseId, lessonId, user), "Successful");
    }

    @GetMapping("/assignment/my")
    public ApiResponse<AssessmentService.Submission> mine(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.mine(courseId, lessonId, user), "Successful");
    }

    @PostMapping("/assignment")
    public ApiResponse<AssessmentService.Submission> submit(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @Valid @RequestBody AssessmentService.SubmissionInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.submit(courseId, lessonId, request, user), "Assignment submitted");
    }

    @GetMapping("/assignment/submissions")
    public ApiResponse<Page<AssessmentService.Submission>> submissions(
        @PathVariable UUID courseId,
        @PathVariable UUID lessonId,
        @AuthenticationPrincipal AuthenticatedUser user,
        Pageable pageable
    ) {
        return ApiResponse.success(service.submissions(courseId, lessonId, user, pageable), "Successful");
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
            "Assignment graded"
        );
    }
}
