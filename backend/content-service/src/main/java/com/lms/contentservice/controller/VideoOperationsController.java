package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.service.VideoRecoveryService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media/operations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class VideoOperationsController {

    private final VideoRecoveryService service;

    private boolean admin(Authentication a) {
        return a
            .getAuthorities()
            .stream()
            .anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping
    public ApiResponse<?> failed(Authentication a) {
        return ApiResponse.success(service.failed(UUID.fromString(a.getName()), admin(a)), "Video işlemleri");
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<?> retry(@PathVariable UUID id, Authentication a) {
        service.retry(id, UUID.fromString(a.getName()), admin(a));
        return ApiResponse.success(null, "İşleme kuyruğuna alındı");
    }
}
