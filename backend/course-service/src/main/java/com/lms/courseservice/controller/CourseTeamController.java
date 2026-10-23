package com.lms.courseservice.controller;

import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.security.AuthenticatedUser;
import com.lms.courseservice.service.CourseTeamService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
public class CourseTeamController {

    private final CourseTeamService service;

    @GetMapping("/{id}/team")
    public ApiResponse<?> get(
        @PathVariable UUID id,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.get(id, user), "Kurs ekibi");
    }

    @PostMapping("/{id}/team")
    public ApiResponse<?> invite(
        @PathVariable UUID id,
        @Valid @RequestBody CourseTeamService.Invite input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(
            service.invite(id, input, user),
            "Davet gönderildi"
        );
    }

    @GetMapping("/invitations")
    public ApiResponse<?> invitations(
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.invitations(user), "Kurs davetleri");
    }

    @PostMapping("/{id}/team/reply")
    public ApiResponse<?> reply(
        @PathVariable UUID id,
        @RequestBody CourseTeamService.Reply input,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.reply(id, input, user);
        return ApiResponse.success(null, "Davet yanıtlandı");
    }

    @DeleteMapping("/{id}/team/{member}")
    public ApiResponse<?> remove(
        @PathVariable UUID id,
        @PathVariable UUID member,
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        service.remove(id, member, user);
        return ApiResponse.success(null, "Ekip üyeliği kaldırıldı");
    }
}
