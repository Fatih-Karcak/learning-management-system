package com.lms.contentservice.domain.entity;

import com.lms.contentservice.domain.enums.AssetType;
import com.lms.contentservice.domain.enums.MediaStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "media_assets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MediaAsset {

    @Id
    private UUID id;   // elle atanır, bkz. yukarıdaki not

    @Column(nullable = false)
    private UUID ownerId;

    private UUID courseId;
    private UUID lessonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaStatus status;

    @Column(nullable = false)
    private String originalFileName;

    private String contentType;
    private Long sizeBytes;

    @Column(nullable = false)
    private String rawStorageKey;

    private String hlsMasterKey;
    private String thumbnailKey;
    private Integer durationSeconds;
    private String failureReason;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
