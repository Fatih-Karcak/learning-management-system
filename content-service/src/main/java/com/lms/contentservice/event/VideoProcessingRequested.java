package com.lms.contentservice.event;

import java.util.UUID;

public record VideoProcessingRequested(UUID mediaId) {
}