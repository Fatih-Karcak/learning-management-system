package com.lms.courseservice.controller;

import com.lms.courseservice.dto.request.CreateSectionRequest;
import com.lms.courseservice.dto.response.ApiResponse;
import com.lms.courseservice.dto.response.SectionResponse;
import com.lms.courseservice.service.SectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @Valid @RequestBody CreateSectionRequest request,
            @RequestHeader("X-User-Id") UUID instructorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        sectionService.createSection(request, instructorId),
                        "Bölüm oluşturuldu"));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<SectionResponse>>> getSections(
            @PathVariable UUID courseId) {
        return ResponseEntity.ok(ApiResponse.success(
                sectionService.getSectionsByCourse(courseId), "Başarılı"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID instructorId) {
        sectionService.deleteSection(id, instructorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Bölüm silindi"));
    }
}