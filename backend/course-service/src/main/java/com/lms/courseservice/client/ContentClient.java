package com.lms.courseservice.client;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange("/api/v1/internal/media")
public interface ContentClient {
    @org.springframework.web.service.annotation.GetExchange("/{id}/metadata")
    Result metadata(@PathVariable UUID id);

    @PutExchange("/{id}/lesson")
    Result linkVideo(@PathVariable UUID id, @RequestBody LinkRequest request);

    record Resource(UUID id, UUID ownerId, UUID courseId, String purpose, String name, String contentType) {}

    record ResourceResult(boolean success, Resource data) {}

    @org.springframework.web.service.annotation.GetExchange("/resources/{id}")
    ResourceResult resource(@PathVariable UUID id);

    record LinkRequest(UUID courseId, UUID lessonId, UUID ownerId) {}

    record Media(String streamUrl, Integer durationSeconds, String status) {
        public Media(String streamUrl, Integer durationSeconds) {
            this(streamUrl, durationSeconds, "READY");
        }
    }

    record Result(boolean success, String message, Media data) {}
}
