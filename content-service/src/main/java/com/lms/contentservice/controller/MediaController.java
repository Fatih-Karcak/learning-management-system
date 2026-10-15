package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.dto.response.MediaAssetResponse;
import com.lms.contentservice.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        mediaService.uploadVideo(file, courseId, lessonId, instructorId),
                        "Video yüklendi, işleniyor"));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            @RequestHeader("X-User-Id") UUID ownerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        mediaService.uploadImage(file, courseId, lessonId, ownerId),
                        "Resim yüklendi"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> getMedia(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(
                ApiResponse.success(mediaService.getMedia(id, requesterId, role), "Başarılı"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        mediaService.deleteMedia(id, requesterId, role);
        return ResponseEntity.ok(ApiResponse.success(null, "Medya silindi"));
    }

    // ── HLS streaming uçları ──

    @GetMapping(value = "/{id}/manifest.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getManifest(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(mediaService.getManifestContent(id, requesterId, role));
    }

    @GetMapping("/{id}/{segmentName:.+\\.ts}")
    public ResponseEntity<InputStreamResource> getSegment(
            @PathVariable UUID id,
            @PathVariable String segmentName,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        InputStream stream = mediaService.getSegmentStream(id, segmentName, requesterId, role);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp2t"))
                .body(new InputStreamResource(stream));
    }
}