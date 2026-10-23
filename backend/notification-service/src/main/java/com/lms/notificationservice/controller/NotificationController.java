package com.lms.notificationservice.controller;

import com.lms.notificationservice.dto.response.ApiResponse;
import com.lms.notificationservice.dto.response.NotificationLogResponse;
import com.lms.notificationservice.mapper.NotificationLogMapper;
import com.lms.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationLogMapper mapper;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<NotificationLogResponse>>> getMyNotifications(
            Authentication authentication,
            Pageable pageable) {
        Page<NotificationLogResponse> result = notificationService
                .getMyNotifications(UUID.fromString(authentication.getName()), pageable)
                .map(mapper::toResponse);
        return ResponseEntity.ok(ApiResponse.success(result, "Başarılı"));
    }
}