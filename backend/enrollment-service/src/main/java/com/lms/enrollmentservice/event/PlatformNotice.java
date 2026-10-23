package com.lms.enrollmentservice.event;

import java.util.UUID;

public record PlatformNotice(
    UUID id,
    UUID recipientId,
    UUID courseId,
    String subject,
    String message,
    String path,
    String kind,
    String token
) {}
