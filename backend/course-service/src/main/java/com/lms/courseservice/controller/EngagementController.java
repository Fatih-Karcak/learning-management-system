package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.EngagementService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class EngagementController {

    private final EngagementService service;

    @GetMapping("/wishlist")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> wishlist(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.success(service.wishlist(u.id()), "İstek listesi");
    }

    @PutMapping("/{id}/wishlist")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> wish(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        service.wish(id, u.id(), true);
        return ApiResponse.success(null, "Listeye eklendi");
    }

    @DeleteMapping("/{id}/wishlist")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> unwish(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        service.wish(id, u.id(), false);
        return ApiResponse.success(null, "Listeden kaldırıldı");
    }

    @GetMapping("/{id}/announcements")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> announcements(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.success(service.announcements(id, u), "Duyurular");
    }

    @PostMapping("/{id}/announcements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> announce(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser u,
        @Valid @RequestBody EngagementService.Announcement a
    ) {
        service.announce(id, u, a);
        return ApiResponse.success(null, "Duyuru yayımlandı");
    }

    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ApiResponse<?> analytics(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.success(service.analytics(id, u), "Kurs raporu");
    }

    @GetMapping("/{id}/recommendations")
    public ApiResponse<?> recommended(@PathVariable UUID id) {
        return ApiResponse.success(service.recommendations(id), "İlgili kurslar");
    }

    @GetMapping("/learning/export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> export(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.success(service.export(u.id()), "Kişisel kurs verileri");
    }

    public record Reason(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 2000)
        String reason
    ) {}

    @PostMapping("/admin/reviews/{id}/remove")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> remove(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser u,
        @Valid @RequestBody Reason reason
    ) {
        service.moderateReview(id, u, reason.reason());
        return ApiResponse.success(null, "Değerlendirme kaldırıldı");
    }
}
