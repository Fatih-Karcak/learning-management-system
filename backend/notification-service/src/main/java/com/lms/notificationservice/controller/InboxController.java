package com.lms.notificationservice.controller;

import com.lms.notificationservice.dto.response.ApiResponse;
import com.lms.notificationservice.service.InboxService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService service;

    private UUID id(Authentication a) {
        return UUID.fromString(a.getName());
    }

    @GetMapping("/inbox")
    public ApiResponse<?> inbox(Authentication a) {
        return ApiResponse.success(service.inbox(id(a)), "Gelen kutusu");
    }

    @PutMapping("/inbox/{notice}/read")
    public ApiResponse<?> read(Authentication a, @PathVariable UUID notice) {
        service.read(id(a), notice);
        return ApiResponse.success(null, "Okundu");
    }

    @GetMapping("/admin/failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> failures() {
        return ApiResponse.success(service.failures(), "Bildirim hataları");
    }

    @PostMapping("/admin/{notice}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> retry(@PathVariable UUID notice) {
        service.retry(notice);
        return ApiResponse.success(null, "Yeniden denenecek");
    }
}
