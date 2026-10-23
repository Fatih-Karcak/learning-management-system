package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.service.LessonNoteService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/progress/{enrollmentId}/notes")
@RequiredArgsConstructor
public class LessonNoteController {

    private final LessonNoteService notes;

    private UUID user(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    @GetMapping
    public ApiResponse<List<LessonNoteService.Note>> list(
        @PathVariable UUID enrollmentId,
        @RequestParam(required = false) UUID lessonId,
        Authentication auth
    ) {
        return ApiResponse.success(notes.list(enrollmentId, lessonId, user(auth)), "Başarılı");
    }

    @PostMapping
    public ApiResponse<LessonNoteService.Note> add(
        @PathVariable UUID enrollmentId,
        @Valid @RequestBody LessonNoteService.Input input,
        Authentication auth
    ) {
        return ApiResponse.success(notes.save(enrollmentId, null, input, user(auth)), "Not kaydedildi");
    }

    @PutMapping("/{id}")
    public ApiResponse<LessonNoteService.Note> edit(
        @PathVariable UUID enrollmentId,
        @PathVariable UUID id,
        @Valid @RequestBody LessonNoteService.Input input,
        Authentication auth
    ) {
        return ApiResponse.success(notes.save(enrollmentId, id, input, user(auth)), "Not güncellendi");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
        @PathVariable UUID enrollmentId,
        @PathVariable UUID id,
        Authentication auth
    ) {
        notes.delete(enrollmentId, id, user(auth));
        return ApiResponse.success(null, "Not silindi");
    }
}
