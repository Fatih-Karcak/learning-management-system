package com.lms.userservice.controller;

import com.lms.userservice.dto.response.ApiResponse;
import com.lms.userservice.dto.response.UserResponse;
import com.lms.userservice.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final com.lms.userservice.service.AdminScopeService scopes;
    private final com.lms.userservice.repository.UserRepository users;

    public record Instructor(UUID id, String displayName) {}

    @GetMapping("/by-email")
    public ApiResponse<UserResponse> byEmail(@org.springframework.web.bind.annotation.RequestParam String email) {
        var user=users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(value->value.getStatus()==com.lms.userservice.domain.enums.UserStatus.ACTIVE&&value.isEmailVerified())
            .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Active verified user not found"));
        return ApiResponse.success(userService.getUserById(user.getId()),"User");
    }

    @GetMapping("/instructor")
    public ApiResponse<Instructor> instructor(
        @org.springframework.web.bind.annotation.RequestParam String email
    ) {
        var user = users
            .findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
            .filter(
                u ->
                    u.getRole() ==
                        com.lms.userservice.domain.enums.UserRole.INSTRUCTOR &&
                    u.getStatus() ==
                        com.lms.userservice.domain.enums.UserStatus.ACTIVE
            )
            .orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Active instructor not found"
                )
            );
        return ApiResponse.success(
            new Instructor(
                user.getId(),
                user.getFirstName() + " " + user.getLastName()
            ),
            "Instructor"
        );
    }

    @GetMapping("/{id}/authorization")
    public ApiResponse<Boolean> allowed(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestParam String path,
        @org.springframework.web.bind.annotation.RequestParam String method
    ) {
        return ApiResponse.success(
            com.lms.userservice.service.AdminScopeService.allows(
                scopes.scope(id),
                path,
                method
            ),
            "Authorization"
        );
    }

    private final com.lms.userservice.service.AccountToolsService tools;

    @GetMapping("/{id}/preferences")
    public ApiResponse<?> preferences(@PathVariable UUID id) {
        return ApiResponse.success(tools.preferences(id), "Preferences");
    }

    private final com.lms.userservice.service.SessionService sessions;

    @GetMapping("/{id}/sessions/{sessionId}")
    public ApiResponse<Boolean> session(
        @PathVariable UUID id,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.success(sessions.active(id, sessionId), "Successful");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(userService.getUserById(id), "Successful")
        );
    }
}
