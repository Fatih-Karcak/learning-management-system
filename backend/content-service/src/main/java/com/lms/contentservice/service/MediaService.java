package com.lms.contentservice.service;

import com.lms.contentservice.client.EnrollmentClient;
import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.AssetType;
import com.lms.contentservice.domain.enums.MediaStatus;
import com.lms.contentservice.dto.response.MediaAssetResponse;
import com.lms.contentservice.event.VideoProcessingListener;
import com.lms.contentservice.event.VideoProcessingRequested;
import com.lms.contentservice.exception.MediaNotFoundException;
import com.lms.contentservice.exception.StorageException;
import com.lms.contentservice.exception.UnauthorizedMediaAccessException;
import com.lms.contentservice.exception.UnsupportedMediaException;
import com.lms.contentservice.mapper.MediaAssetMapper;
import com.lms.contentservice.repository.MediaAssetRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final ApplicationEventPublisher eventPublisher;
    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;
    private final VideoProcessingService videoProcessingService;
    private final EnrollmentClient enrollmentClient;
    private final MediaAssetMapper mediaAssetMapper;
    private final CourseIdentityClient courses;

    @Value("${minio.bucket.raw}")
    private String rawBucket;

    @Value("${minio.bucket.hls}")
    private String hlsBucket;

    @Value("${minio.bucket.images}")
    private String imagesBucket;

    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2 GB
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10 MB

    @Transactional
    public MediaAssetResponse registerUploadedVideo(UUID id, UUID owner, UUID course, String name,
        String contentType, long size, String rawKey) {
        requireCourseEditor(course, owner);
        var existing = mediaAssetRepository.findById(id);
        if (existing.isPresent()) return toResponse(existing.get());
        var asset = MediaAsset.builder().id(id).ownerId(owner).courseId(course).type(AssetType.VIDEO)
            .status(MediaStatus.PROCESSING).originalFileName(name).contentType(contentType)
            .sizeBytes(size).rawStorageKey(rawKey).build();
        mediaAssetRepository.save(asset);
        eventPublisher.publishEvent(new VideoProcessingRequested(id));
        return toResponse(asset);
    }

    @Transactional
    public MediaAssetResponse uploadVideo(
        MultipartFile file,
        UUID courseId,
        UUID lessonId,
        UUID instructorId
    ) {
        requireCourseEditor(courseId, instructorId);
        if (file.isEmpty()) throw new UnsupportedMediaException(
            "The file must not be empty"
        );

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new UnsupportedMediaException(
                "Only video files are accepted: " + contentType
            );
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new UnsupportedMediaException(
                "The video exceeds the 2 GB size limit"
            );
        }

        UUID mediaId = UUID.randomUUID();
        String rawKey =
            "raw/" + mediaId + "/" + sanitize(file.getOriginalFilename());

        try {
            storageService.upload(
                rawBucket,
                rawKey,
                file.getInputStream(),
                file.getSize(),
                contentType
            );
        } catch (IOException e) {
            throw new StorageException("The video could not be uploaded: " + e.getMessage());
        }

        MediaAsset asset = MediaAsset.builder()
            .id(mediaId)
            .ownerId(instructorId)
            .courseId(courseId)
            .lessonId(lessonId)
            .type(AssetType.VIDEO)
            .status(MediaStatus.PROCESSING)
            .originalFileName(file.getOriginalFilename())
            .contentType(contentType)
            .sizeBytes(file.getSize())
            .rawStorageKey(rawKey)
            .build();

        asset = mediaAssetRepository.save(asset);

        eventPublisher.publishEvent(
            new VideoProcessingRequested(asset.getId())
        );
        log.info(
            "Video record created; processing starts after commit: {}",
            asset.getId()
        );

        return toResponse(asset);
    }

    @Transactional
    public MediaAssetResponse uploadImage(
        MultipartFile file,
        UUID courseId,
        UUID lessonId,
        UUID ownerId
    ) {
        requireCourseEditor(courseId, ownerId);
        if (file.isEmpty()) throw new UnsupportedMediaException(
            "The file must not be empty"
        );

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new UnsupportedMediaException(
                "Only image files are accepted: " + contentType
            );
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new UnsupportedMediaException(
                "The image exceeds the 10 MB size limit"
            );
        }

        try {
            byte[] data = file.getBytes();
            boolean png =
                data.length >= 8 &&
                data[0] == (byte) 0x89 &&
                data[1] == 'P' &&
                data[2] == 'N' &&
                data[3] == 'G';
            boolean jpeg =
                data.length >= 3 &&
                data[0] == (byte) 0xff &&
                data[1] == (byte) 0xd8 &&
                data[2] == (byte) 0xff;
            boolean webp =
                data.length >= 12 &&
                new String(
                    data,
                    0,
                    4,
                    java.nio.charset.StandardCharsets.US_ASCII
                ).equals("RIFF") &&
                new String(
                    data,
                    8,
                    4,
                    java.nio.charset.StandardCharsets.US_ASCII
                ).equals("WEBP");
            if (!png && !jpeg && !webp) throw new UnsupportedMediaException(
                "Only valid JPEG, PNG and WebP images are accepted"
            );
            contentType = png
                ? "image/png"
                : jpeg
                  ? "image/jpeg"
                  : "image/webp";
        } catch (IOException e) {
            throw new StorageException("The image could not be read");
        }
        UUID mediaId = UUID.randomUUID();
        String key =
            "images/" + mediaId + "/" + sanitize(file.getOriginalFilename());

        try {
            storageService.upload(
                imagesBucket,
                key,
                file.getInputStream(),
                file.getSize(),
                contentType
            );
        } catch (IOException e) {
            throw new StorageException("The image could not be uploaded: " + e.getMessage());
        }

        MediaAsset asset = MediaAsset.builder()
            .id(mediaId)
            .ownerId(ownerId)
            .courseId(courseId)
            .lessonId(lessonId)
            .type(AssetType.IMAGE)
            .status(MediaStatus.READY)
            .originalFileName(file.getOriginalFilename())
            .contentType(contentType)
            .sizeBytes(file.getSize())
            .rawStorageKey(key)
            .build();

        asset = mediaAssetRepository.save(asset);
        return toResponse(asset);
    }

    public MediaAssetResponse getMedia(
        UUID mediaId,
        UUID requesterId,
        String requesterRole
    ) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);
        return toResponse(asset);
    }

    public MediaAssetResponse getInternalMetadata(UUID mediaId) {
        return toResponse(findById(mediaId));
    }

    @Transactional
    public MediaAssetResponse linkVideoToLesson(
        UUID mediaId,
        UUID courseId,
        UUID lessonId,
        UUID ownerId
    ) {
        MediaAsset asset = findById(mediaId);
        if (
            !asset.getOwnerId().equals(ownerId) &&
            !(
                courseId.equals(asset.getCourseId()) &&
                courses.canEdit(courseId, ownerId)
            )
        ) {
            throw new UnauthorizedMediaAccessException(
                "You can only attach your own video to this lesson"
            );
        }
        if (
            asset.getType() != AssetType.VIDEO ||
            asset.getStatus() == MediaStatus.FAILED
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Failed media cannot be attached; upload the video again"
            );
        }
        if (
            asset.getCourseId() != null && !asset.getCourseId().equals(courseId)
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The video belongs to another course; upload it again for this course"
            );
        }
        asset.setCourseId(courseId);
        asset.setLessonId(lessonId);
        mediaAssetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MediaAssetResponse> getOwnMedia(
        UUID ownerId,
        org.springframework.data.domain.Pageable pageable
    ) {
        return mediaAssetRepository
            .findByOwnerId(ownerId, pageable)
            .map(this::toResponse);
    }

    public org.springframework.data.domain.Page<MediaAssetResponse> getCourseLibrary(
        UUID course,
        UUID user,
        org.springframework.data.domain.Pageable pageable
    ) {
        requireCourseEditor(course, user);
        return mediaAssetRepository
            .findCourseLibrary(course, user, pageable)
            .map(this::toResponse);
    }

    private void requireCourseEditor(UUID course, UUID user) {
        if (
            course != null && !courses.canEdit(course, user)
        ) throw new UnauthorizedMediaAccessException(
            "You do not have permission to add content to this course"
        );
    }

    public org.springframework.data.domain.Page<MediaAssetResponse> searchLibrary(UUID course, UUID user,
        AssetType type, MediaStatus status, String query, org.springframework.data.domain.Pageable pageable) {
        requireCourseEditor(course, user);
        if (query.length() > 200) throw new IllegalArgumentException("Search text must be at most 200 characters");
        return mediaAssetRepository.searchLibrary(course, user, type, status, query.trim(), pageable).map(this::toResponse);
    }

    public record Cover(String contentType, InputStream stream) {}

    public Cover getPublicCover(UUID id,UUID courseHint) {
        var asset = findById(id);
        UUID course=asset.getCourseId()==null?courseHint:asset.getCourseId();
        if (asset.getType() != AssetType.IMAGE || asset.getStatus() != MediaStatus.READY ||
            course == null || !courses.isPublishedCover(course,id,asset.getCourseId()==null?asset.getOwnerId():null))
            throw new MediaNotFoundException("Course cover not found");
        return new Cover(asset.getContentType(), storageService.download(imagesBucket, asset.getRawStorageKey()));
    }

    @Transactional
    public void deleteMedia(
        UUID mediaId,
        UUID requesterId,
        String requesterRole
    ) {
        MediaAsset asset = findById(mediaId);
        if (
            !asset.getOwnerId().equals(requesterId) &&
            !"ADMIN".equalsIgnoreCase(requesterRole)
        ) {
            throw new UnauthorizedMediaAccessException(
                "You do not have permission to delete this media"
            );
        }

        if (asset.getLessonId() != null) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Media attached to a lesson cannot be deleted; existing learner access is preserved"
        );
        if (asset.getType() == AssetType.IMAGE && asset.getCourseId() != null &&
            courses.isPublishedCover(asset.getCourseId(), mediaId)) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Replace the published course cover before deleting this image");
        storageService.delete(
            asset.getType() == AssetType.IMAGE ? imagesBucket : rawBucket,
            asset.getRawStorageKey()
        );
        if (asset.getThumbnailKey() != null) {
            storageService.delete(imagesBucket, asset.getThumbnailKey());
        }
        if (asset.getHlsMasterKey() != null) {
            String prefix = asset
                .getHlsMasterKey()
                .substring(0, asset.getHlsMasterKey().lastIndexOf('/') + 1);
            storageService.deletePrefix(hlsBucket, prefix);
        }

        mediaAssetRepository.delete(asset);
    }

    // ── HLS streaming ──

    public String getManifestContent(
        UUID mediaId,
        UUID requesterId,
        String requesterRole
    ) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);

        if (
            asset.getType() != AssetType.VIDEO ||
            asset.getStatus() != MediaStatus.READY ||
            asset.getHlsMasterKey() == null
        ) {
            throw new MediaNotFoundException(
                "The video is not ready or was not found"
            );
        }

        try (
            InputStream in = storageService.download(
                hlsBucket,
                asset.getHlsMasterKey()
            )
        ) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new StorageException("The manifest could not be read: " + e.getMessage());
        }
    }

    public InputStream getSegmentStream(
        UUID mediaId,
        String segmentName,
        UUID requesterId,
        String requesterRole
    ) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);

        if (asset.getStatus() != MediaStatus.READY || asset.getType() != AssetType.VIDEO || asset.getHlsMasterKey() == null) {
            throw new MediaNotFoundException("The video is not ready yet");
        }
        if (!segmentName.matches("(?:master[0-9]+|quality-[0-9]+-[0-9]+)\\.ts"))
            throw new MediaNotFoundException("Invalid video segment");

        String hlsPrefix = asset
            .getHlsMasterKey()
            .substring(0, asset.getHlsMasterKey().lastIndexOf('/') + 1);

        return storageService.download(hlsBucket, hlsPrefix + segmentName);
    }

    public String getVariantContent(UUID mediaId, String name, UUID requester, String role) {
        if (!name.matches("quality-[0-9]+\\.m3u8")) throw new MediaNotFoundException("Invalid video quality");
        var asset = findById(mediaId);
        checkAccess(asset, requester, role);
        if (asset.getStatus() != MediaStatus.READY || asset.getHlsMasterKey() == null)
            throw new MediaNotFoundException("Video is not ready");
        String prefix = asset.getHlsMasterKey().substring(0, asset.getHlsMasterKey().lastIndexOf('/')+1);
        try (var stream=storageService.download(hlsBucket,prefix+name)) {
            return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new StorageException("Could not read video quality"); }
    }

    // ── Yardımcılar ──

    private void checkAccess(
        MediaAsset asset,
        UUID requesterId,
        String requesterRole
    ) {
        if (asset.getOwnerId().equals(requesterId)) return;
        if ("ADMIN".equalsIgnoreCase(requesterRole)) return;

        if (asset.getCourseId() == null) {
            throw new UnauthorizedMediaAccessException(
                "You do not have permission to access this content"
            );
        }

        if (
            requesterId != null &&
            courses.canEdit(asset.getCourseId(), requesterId)
        ) return;

        if (
            asset.getType() == AssetType.VIDEO &&
            courses.preview(asset.getCourseId(), asset.getId())
        ) return;
        if (requesterId == null) throw new UnauthorizedMediaAccessException(
            "Enroll in this course to watch this video"
        );
        boolean enrolled = Boolean.TRUE.equals(
            enrollmentClient
                .checkEnrollment(asset.getCourseId(), requesterId)
                .data()
        );

        if (!enrolled) {
            throw new UnauthorizedMediaAccessException(
                "You must be enrolled to view this content"
            );
        }
        if(asset.getType()==AssetType.VIDEO&&!courses.canLearn(asset.getCourseId(),asset.getId(),requesterId))
            throw new UnauthorizedMediaAccessException("Complete prerequisite lessons or wait for the release date");
    }

    private MediaAssetResponse toResponse(MediaAsset asset) {
        MediaAssetResponse response = mediaAssetMapper.toResponse(asset);

        if (asset.getType() == AssetType.VIDEO) {
            response.setStreamUrl(
                "/api/v1/media/" + asset.getId() + "/manifest.m3u8"
            );
        } else if (asset.getStatus() == MediaStatus.READY) {
            response.setPublicUrl("/api/v1/media/" + asset.getId() + "/cover");
            response.setStreamUrl(
                storageService.presignedGetUrl(
                    imagesBucket,
                    asset.getRawStorageKey(),
                    3600
                )
            );
        }

        if (asset.getThumbnailKey() != null) {
            response.setThumbnailUrl(
                storageService.presignedGetUrl(
                    imagesBucket,
                    asset.getThumbnailKey(),
                    3600
                )
            );
        }

        return response;
    }

    private MediaAsset findById(UUID id) {
        return mediaAssetRepository
            .findById(id)
            .orElseThrow(() ->
                new MediaNotFoundException("Media not found: " + id)
            );
    }

    private String sanitize(String fileName) {
        if (fileName == null) return "file";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
