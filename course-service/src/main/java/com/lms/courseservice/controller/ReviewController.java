package com.lms.courseservice.controller;

import com.lms.courseservice.dto.request.CreateReviewRequest;
import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.dto.response.ReviewResponse;
import com.lms.courseservice.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.lms.courseservice.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/course/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "X-User-Avatar", required = false) String avatarUrl) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        reviewService.createReview(courseId, request, user.id(),
                                user.displayName(), avatarUrl),
                        "Yorum eklendi"));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getCourseReviews(
            @PathVariable UUID courseId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getCourseReviews(courseId, pageable), "Başarılı"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        reviewService.deleteReview(id, user.id());
        return ResponseEntity.ok(ApiResponse.success(null, "Yorum silindi"));
    }
}