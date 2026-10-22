package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.*;
import com.lms.enrollmentservice.service.LearningManagementService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class LearningManagementController {

    private final LearningManagementService service;

    private UUID id(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    @PostMapping("/free/{courseId}")
    public ApiResponse<EnrollmentResponse> free(@PathVariable UUID courseId, Authentication auth) {
        return ApiResponse.success(service.enrollFree(courseId, id(auth)), "Kursa kaydoldunuz");
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(Authentication auth) {
        return ApiResponse.success(service.summary(id(auth)), "Başarılı");
    }

    @GetMapping("/courses/{courseId}/students")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<Page<LearningManagementService.RosterRow>> roster(
        @PathVariable UUID courseId,
        Authentication auth,
        Pageable pageable
    ) {
        return ApiResponse.success(
            service.roster(
                courseId,
                id(auth),
                auth
                    .getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                pageable
            ),
            "Başarılı"
        );
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> adminSummary() {
        return ApiResponse.success(service.summary(null), "Başarılı");
    }

    @PostMapping("/admin/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrollmentResponse> grant(
        @Valid @RequestBody LearningManagementService.Grant request,
        Authentication auth
    ) {
        return ApiResponse.success(service.grant(request, id(auth)), "Kayıt oluşturuldu");
    }

    @PostMapping("/admin/{enrollmentId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> revoke(
        @PathVariable UUID enrollmentId,
        @RequestBody Map<String, String> request,
        Authentication auth
    ) {
        service.revoke(enrollmentId, request.get("reason"), id(auth));
        return ApiResponse.success(null, "Kayıt iptal edildi");
    }
}
