package com.lms.contentservice.dto.response;

import com.lms.contentservice.domain.enums.AssetType;
import com.lms.contentservice.domain.enums.MediaStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MediaAssetResponse {
    private UUID id;
    private AssetType type;
    private MediaStatus status;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private Integer durationSeconds;
    private String streamUrl;
    private String thumbnailUrl;
    private String failureReason;
    private LocalDateTime createdAt;
}