package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.service.ResourceService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/media/resources")
public class InternalResourceController {

    private final ResourceService service;
    private final String key;

    public InternalResourceController(ResourceService service, @Value("${internal.api-key:}") String key) {
        this.service = service;
        this.key = key;
    }

    @GetMapping("/{id}")
    public ApiResponse<?> metadata(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Internal-Service-Key", required = false) String supplied
    ) {
        if (
            key.isBlank() ||
            supplied == null ||
            !java.security.MessageDigest.isEqual(
                key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        ) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ApiResponse.success(service.metadata(id), "Kaynak");
    }
}
