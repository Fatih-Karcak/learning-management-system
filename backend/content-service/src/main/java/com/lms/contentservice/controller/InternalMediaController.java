package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.dto.response.MediaAssetResponse;
import com.lms.contentservice.service.MediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Course service verifies course ownership before linking the instructor's media. */
@RestController
@RequestMapping("/api/v1/internal/media")
public class InternalMediaController {
    private final MediaService mediaService;
    private final String internalKey;

    public InternalMediaController(MediaService mediaService, @Value("${internal.api-key:}") String internalKey) {
        this.mediaService = mediaService;
        this.internalKey = internalKey;
    }

    public record LinkRequest(@NotNull UUID courseId, @NotNull UUID lessonId, @NotNull UUID ownerId) {}

    @PutMapping("/{id}/lesson")
    public ApiResponse<MediaAssetResponse> linkVideo(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String key,
            @Valid @RequestBody LinkRequest request) {
        if (internalKey.isBlank() || key == null || !MessageDigest.isEqual(
                internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Servis kimliği doğrulanamadı");
        }
        return ApiResponse.success(mediaService.linkVideoToLesson(
                id, request.courseId(), request.lessonId(), request.ownerId()), "Video derse bağlandı");
    }
}
