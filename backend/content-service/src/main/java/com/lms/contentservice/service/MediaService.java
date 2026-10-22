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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

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

    @Value("${minio.bucket.raw}")    private String rawBucket;
    @Value("${minio.bucket.hls}")    private String hlsBucket;
    @Value("${minio.bucket.images}") private String imagesBucket;

    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2 GB
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;       // 10 MB

    @Transactional
    public MediaAssetResponse uploadVideo(MultipartFile file, UUID courseId, UUID lessonId, UUID instructorId) {
        if (file.isEmpty()) throw new UnsupportedMediaException("Dosya boş olamaz");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new UnsupportedMediaException("Sadece video dosyaları kabul edilir: " + contentType);
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new UnsupportedMediaException("Video boyutu 2GB sınırını aşıyor");
        }

        UUID mediaId = UUID.randomUUID();
        String rawKey = "raw/" + mediaId + "/" + sanitize(file.getOriginalFilename());

        try {
            storageService.upload(rawBucket, rawKey, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Video yüklenemedi: " + e.getMessage());
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

        eventPublisher.publishEvent(new VideoProcessingRequested(asset.getId()));
        log.info("Video kaydı oluşturuldu commit sonra eklenecek {}", asset.getId());


        return toResponse(asset);
    }

    @Transactional
    public MediaAssetResponse uploadImage(MultipartFile file, UUID courseId, UUID lessonId, UUID ownerId) {
        if (file.isEmpty()) throw new UnsupportedMediaException("Dosya boş olamaz");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new UnsupportedMediaException("Sadece resim dosyaları kabul edilir: " + contentType);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new UnsupportedMediaException("Resim boyutu 10MB sınırını aşıyor");
        }

        UUID mediaId = UUID.randomUUID();
        String key = "images/" + mediaId + "/" + sanitize(file.getOriginalFilename());

        try {
            storageService.upload(imagesBucket, key, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Resim yüklenemedi: " + e.getMessage());
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

    public MediaAssetResponse getMedia(UUID mediaId, UUID requesterId, String requesterRole) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);
        return toResponse(asset);
    }

    public MediaAssetResponse getInternalMetadata(UUID mediaId) {return toResponse(findById(mediaId));}

    @Transactional
    public MediaAssetResponse linkVideoToLesson(UUID mediaId, UUID courseId, UUID lessonId, UUID ownerId) {
        MediaAsset asset = findById(mediaId);
        if (!asset.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedMediaAccessException("Yalnızca kendi videonuzu derse ekleyebilirsiniz");
        }
        if (asset.getType() != AssetType.VIDEO || asset.getStatus() == MediaStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "İşlenemeyen dosya derse eklenemez; videoyu yeniden yükleyin");
        }
        if (asset.getCourseId() != null && !asset.getCourseId().equals(courseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video başka bir kursa bağlı; bu kurs için yeniden yükleyin");
        }
        asset.setCourseId(courseId);
        asset.setLessonId(lessonId);
        mediaAssetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MediaAssetResponse> getOwnMedia(
            UUID ownerId, org.springframework.data.domain.Pageable pageable) {
        return mediaAssetRepository.findByOwnerId(ownerId, pageable).map(this::toResponse);
    }

    @Transactional
    public void deleteMedia(UUID mediaId, UUID requesterId, String requesterRole) {
        MediaAsset asset = findById(mediaId);
        if (!asset.getOwnerId().equals(requesterId) && !"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedMediaAccessException("Bu medyayı silme yetkiniz yok");
        }

        storageService.delete(asset.getType() == AssetType.IMAGE ? imagesBucket : rawBucket,
                asset.getRawStorageKey());
        if (asset.getThumbnailKey() != null) {
            storageService.delete(imagesBucket, asset.getThumbnailKey());
        }
        if (asset.getHlsMasterKey() != null) {
            String prefix = asset.getHlsMasterKey().substring(0, asset.getHlsMasterKey().lastIndexOf('/') + 1);
            storageService.deletePrefix(hlsBucket, prefix);
        }

        mediaAssetRepository.delete(asset);
    }

    // ── HLS streaming ──

    public String getManifestContent(UUID mediaId, UUID requesterId, String requesterRole) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);

        if (asset.getType() != AssetType.VIDEO
                || asset.getStatus() != MediaStatus.READY
                || asset.getHlsMasterKey() == null) {
            throw new MediaNotFoundException("Video henüz hazır değil veya bulunamadı");
        }

        try (InputStream in = storageService.download(hlsBucket, asset.getHlsMasterKey())) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new StorageException("Manifest okunamadı: " + e.getMessage());
        }
    }

    public InputStream getSegmentStream(UUID mediaId, String segmentName, UUID requesterId, String requesterRole) {
        MediaAsset asset = findById(mediaId);
        checkAccess(asset, requesterId, requesterRole);

        if (asset.getHlsMasterKey() == null) {
            throw new MediaNotFoundException("Video henüz hazır değil");
        }

        String hlsPrefix = asset.getHlsMasterKey().substring(
                0, asset.getHlsMasterKey().lastIndexOf('/') + 1);

        return storageService.download(hlsBucket, hlsPrefix + segmentName);
    }

    // ── Yardımcılar ──

    private void checkAccess(MediaAsset asset, UUID requesterId, String requesterRole) {
        if (asset.getOwnerId().equals(requesterId)) return;
        if ("ADMIN".equalsIgnoreCase(requesterRole)) return;

        if (asset.getCourseId() == null) {
            throw new UnauthorizedMediaAccessException("Bu içeriğe erişim yetkiniz yok");
        }

        boolean enrolled = Boolean.TRUE.equals(
                enrollmentClient.checkEnrollment(asset.getCourseId(), requesterId).data());

        if (!enrolled) {
            throw new UnauthorizedMediaAccessException(
                    "Bu içeriği görüntülemek için kursa kayıtlı olmalısınız");
        }
    }

    private MediaAssetResponse toResponse(MediaAsset asset) {
        MediaAssetResponse response = mediaAssetMapper.toResponse(asset);

        if (asset.getType() == AssetType.VIDEO) {
            response.setStreamUrl("/api/v1/media/" + asset.getId() + "/manifest.m3u8");
        } else if (asset.getStatus() == MediaStatus.READY) {
            response.setStreamUrl(storageService.presignedGetUrl(imagesBucket, asset.getRawStorageKey(), 3600));
        }

        if (asset.getThumbnailKey() != null) {
            response.setThumbnailUrl(storageService.presignedGetUrl(imagesBucket, asset.getThumbnailKey(), 3600));
        }

        return response;
    }

    private MediaAsset findById(UUID id) {
        return mediaAssetRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException("Medya bulunamadı: " + id));
    }

    private String sanitize(String fileName) {
        if (fileName == null) return "file";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
