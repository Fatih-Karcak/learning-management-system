package com.lms.contentservice.controller;

import com.lms.contentservice.dto.response.ApiResponse;
import com.lms.contentservice.service.ResumableUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/media/uploads")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
public class ResumableUploadController {
  private final ResumableUploadService service;

  private UUID user(Authentication auth) {
    return UUID.fromString(auth.getName());
  }

  @GetMapping
  public ApiResponse<?> pending(Authentication auth) {
    return ApiResponse.success(service.pending(user(auth)), "Pending uploads");
  }

  @PostMapping
  public ApiResponse<?> start(
      @Valid @RequestBody ResumableUploadService.Start input, Authentication auth) {
    return ApiResponse.success(service.start(input, user(auth)), "Upload created");
  }

  @GetMapping("/{id}")
  public ApiResponse<?> status(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.success(service.status(id, user(auth)), "Upload status");
  }

  @PutMapping(value = "/{id}/parts/{index}", consumes = "application/octet-stream")
  public ApiResponse<?> part(
      @PathVariable UUID id,
      @PathVariable int index,
      @RequestHeader("Upload-Checksum") String checksum,
      HttpServletRequest request,
      Authentication auth)
      throws IOException {
    if (request.getContentLengthLong() > ResumableUploadService.CHUNK_BYTES)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Chunk exceeds 8 MiB");
    byte[] bytes = request.getInputStream().readNBytes(ResumableUploadService.CHUNK_BYTES + 1);
    if (bytes.length > ResumableUploadService.CHUNK_BYTES)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Chunk exceeds 8 MiB");
    return ApiResponse.success(service.put(id, index, bytes, checksum, user(auth)), "Chunk saved");
  }

  @PostMapping("/{id}/complete")
  public ApiResponse<?> complete(@PathVariable UUID id, Authentication auth) {
    return ApiResponse.success(service.complete(id, user(auth)), "Video uploaded");
  }

  @DeleteMapping("/{id}")
  public ApiResponse<?> cancel(@PathVariable UUID id, Authentication auth) {
    service.cancel(id, user(auth));
    return ApiResponse.success(null, "Upload cancelled");
  }
}
