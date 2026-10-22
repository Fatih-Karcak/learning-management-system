package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.DiscussionService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/questions")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionService service;

    @GetMapping
    public ApiResponse<Page<DiscussionService.Question>> list(
        @PathVariable UUID courseId,
        @RequestParam(required = false) UUID lessonId,
        @RequestParam(defaultValue = "") String query,
        @RequestParam(defaultValue = "false") boolean unanswered,
        @AuthenticationPrincipal AuthenticatedUser user,
        Pageable pageable
    ) {
        return ApiResponse.success(
            service.list(courseId, lessonId, query, unanswered, user, pageable),
            "Başarılı"
        );
    }

    @PostMapping
    public ApiResponse<DiscussionService.Question> ask(
        @PathVariable UUID courseId,
        @Valid @RequestBody DiscussionService.QuestionInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.ask(courseId, request, user), "Soru kaydedildi");
    }

    @GetMapping("/{questionId}/replies")
    public ApiResponse<List<DiscussionService.Reply>> replies(
        @PathVariable UUID courseId,
        @PathVariable UUID questionId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.replies(courseId, questionId, user), "Başarılı");
    }

    @PostMapping("/{questionId}/replies")
    public ApiResponse<DiscussionService.Reply> reply(
        @PathVariable UUID courseId,
        @PathVariable UUID questionId,
        @Valid @RequestBody DiscussionService.ReplyInput request,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.reply(courseId, questionId, request, user), "Yanıt kaydedildi");
    }

    @PutMapping("/{questionId}/accepted/{replyId}")
    public ApiResponse<Void> accept(
        @PathVariable UUID courseId,
        @PathVariable UUID questionId,
        @PathVariable UUID replyId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.accept(courseId, questionId, replyId, user);
        return ApiResponse.success(null, "Yanıt kabul edildi");
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> delete(
        @PathVariable UUID courseId,
        @PathVariable UUID questionId,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.delete(courseId, questionId, user);
        return ApiResponse.success(null, "Soru silindi");
    }
}
