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

    @GetMapping("/{id}/authorization")
    public ApiResponse<Boolean> allowed(
        @PathVariable UUID id,
        @org.springframework.web.bind.annotation.RequestParam String path,
        @org.springframework.web.bind.annotation.RequestParam String method
    ) {
        return ApiResponse.success(
            com.lms.userservice.service.AdminScopeService.allows(scopes.scope(id), path, method),
            "Yetki"
        );
    }

    private final com.lms.userservice.service.AccountToolsService tools;

    @GetMapping("/{id}/preferences")
    public ApiResponse<?> preferences(@PathVariable UUID id) {
        return ApiResponse.success(tools.preferences(id), "Tercihler");
    }

    private final com.lms.userservice.service.SessionService sessions;

    @GetMapping("/{id}/sessions/{sessionId}")
    public ApiResponse<Boolean> session(@PathVariable UUID id, @PathVariable UUID sessionId) {
        return ApiResponse.success(sessions.active(id, sessionId), "Başarılı");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id), "Başarılı"));
    }
}
