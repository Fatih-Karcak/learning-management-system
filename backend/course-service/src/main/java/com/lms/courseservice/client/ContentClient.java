package com.lms.courseservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.UUID;

@HttpExchange("/api/v1/internal/media")
public interface ContentClient {
    @PutExchange("/{id}/lesson")
    Result linkVideo(@PathVariable UUID id, @RequestBody LinkRequest request);

    record LinkRequest(UUID courseId, UUID lessonId, UUID ownerId) {}
    record Media(String streamUrl, Integer durationSeconds) {}
    record Result(boolean success, String message, Media data) {}
}
