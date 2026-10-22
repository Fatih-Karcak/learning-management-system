package com.lms.contentservice.repository;

import com.lms.contentservice.domain.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    List<MediaAsset> findByLessonId(UUID lessonId);
    List<MediaAsset> findByCourseId(UUID courseId);
    List<MediaAsset> findByOwnerId(UUID ownerId);
    org.springframework.data.domain.Page<MediaAsset> findByOwnerId(
            UUID ownerId, org.springframework.data.domain.Pageable pageable);
}
