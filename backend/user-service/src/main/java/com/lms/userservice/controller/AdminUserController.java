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
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

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
