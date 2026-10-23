package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.service.ResourceService;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService service;

    private String role(Authentication a) {
        return a
            .getAuthorities()
            .stream()
            .map(x -> x.getAuthority())
            .filter(x -> x.startsWith("ROLE_"))
            .map(x -> x.substring(5))
            .findFirst()
            .orElse("");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(
        @RequestParam MultipartFile file,
        @RequestParam UUID courseId,
        @RequestParam String purpose,
        Authentication a
    ) throws IOException {
        return ApiResponse.success(
            service.upload(file, courseId, purpose, UUID.fromString(a.getName()), role(a)),
            "Dosya yüklendi"
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id, Authentication a) {
        var d = service.download(id, UUID.fromString(a.getName()), role(a));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(d.resource().contentType()))
            .contentLength(d.resource().sizeBytes())
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "private, no-store")
            .header(
                "Content-Disposition",
                ContentDisposition.attachment()
                    .filename(d.resource().name(), java.nio.charset.StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new InputStreamResource(d.stream()));
    }
}
