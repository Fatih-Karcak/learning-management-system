package com.lms.contentservice.repository;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.MediaStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    @Query(
        "select m from MediaAsset m where m.courseId=:course or (m.ownerId=:owner and m.courseId is null)"
    )
    org.springframework.data.domain.Page<MediaAsset> findCourseLibrary(
        UUID course,
        UUID owner,
        org.springframework.data.domain.Pageable pageable
    );

    // Update processing fields only: an instructor can link the video while FFmpeg is still running.
    @Modifying
    @Transactional
    @Query(
        "update MediaAsset m set m.status = :status, m.durationSeconds = :duration, " +
            "m.hlsMasterKey = :manifest, m.thumbnailKey = :thumbnail, m.failureReason = null, " +
            "m.updatedAt = CURRENT_TIMESTAMP where m.id = :id"
    )
    void completeProcessing(
        UUID id,
        MediaStatus status,
        int duration,
        String manifest,
        String thumbnail
    );

    @Modifying
    @Transactional
    @Query(
        "update MediaAsset m set m.status = :status, m.failureReason = :reason, " +
            "m.updatedAt = CURRENT_TIMESTAMP where m.id = :id"
    )
    void failProcessing(UUID id, MediaStatus status, String reason);

    List<MediaAsset> findByLessonId(UUID lessonId);
    List<MediaAsset> findByCourseId(UUID courseId);
    List<MediaAsset> findByOwnerId(UUID ownerId);
    org.springframework.data.domain.Page<MediaAsset> findByOwnerId(
        UUID ownerId,
        org.springframework.data.domain.Pageable pageable
    );
}
