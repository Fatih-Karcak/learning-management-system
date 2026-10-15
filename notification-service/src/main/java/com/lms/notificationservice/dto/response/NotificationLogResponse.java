package com.lms.notificationservice.dto.response;

import com.lms.notificationservice.domain.enums.NotificationStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
@Data

public class NotificationLogResponse {
    private UUID id;
    private String subject;
    private String eventType;
    private NotificationStatus status;
    private LocalDateTime createdAt;
}
