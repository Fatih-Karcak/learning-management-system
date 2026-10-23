package com.lms.userservice.controller;

import com.lms.userservice.dto.response.*;
import com.lms.userservice.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/admin")
@PreAuthorize(
    "hasRole('ADMIN') and @adminScopeService.scope(T(java.util.UUID).fromString(authentication.name)) == 'FULL'"
)
@RequiredArgsConstructor
public class AdminUserController {

    private final com.lms.userservice.service.AdminScopeService scopes;

    public record Scope(
        @jakarta.validation.constraints.Pattern(regexp = "FULL|MODERATION|SUPPORT")
        @jakarta.validation.constraints.NotBlank
        String scope,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 1000)
        String reason
    ) {}

    @PutMapping("/{id}/scope")
    public ApiResponse<?> scope(
        @PathVariable UUID id,
        @Valid @RequestBody Scope input,
        org.springframework.security.core.Authentication auth
    ) {
        scopes.set(id, input.scope(), input.reason(), UUID.fromString(auth.getName()));
        return ApiResponse.success(null, "Yetki kapsamı güncellendi");
    }

    private final AdminUserService service;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(service.summary(), "Başarılı");
    }

    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.audit(page), "Başarılı");
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody AdminUserService.Update request,
        Authentication auth
    ) {
        return ApiResponse.success(
            service.update(id, request, UUID.fromString(auth.getName())),
            "Kullanıcı güncellendi"
        );
    }
}
