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
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<MediaAssetResponse>>> getOwnMedia(
            Authentication authentication, org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaService.getOwnMedia(UUID.fromString(authentication.getName()), pageable), "Başarılı"));
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        mediaService.uploadVideo(file, courseId, lessonId, UUID.fromString(authentication.getName())),
                        "Video yüklendi, işleniyor"));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID lessonId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        mediaService.uploadImage(file, courseId, lessonId, UUID.fromString(authentication.getName())),
                        "Resim yüklendi"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> getMedia(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(mediaService.getMedia(id, UUID.fromString(authentication.getName()), role(authentication)), "Başarılı"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID id,
            Authentication authentication) {
        mediaService.deleteMedia(id, UUID.fromString(authentication.getName()), role(authentication));
        return ResponseEntity.ok(ApiResponse.success(null, "Medya silindi"));
    }

    // ── HLS streaming uçları ──

    @GetMapping(value = "/{id}/manifest.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getManifest(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(mediaService.getManifestContent(id, UUID.fromString(authentication.getName()), role(authentication)));
    }

    @GetMapping("/{id}/{segmentName:.+\\.ts}")
    public ResponseEntity<InputStreamResource> getSegment(
            @PathVariable UUID id,
            @PathVariable String segmentName,
            Authentication authentication) {
        InputStream stream = mediaService.getSegmentStream(id, segmentName, UUID.fromString(authentication.getName()), role(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp2t"))
                .body(new InputStreamResource(stream));
    }
    private String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5)).findFirst().orElse("");
    }
}
