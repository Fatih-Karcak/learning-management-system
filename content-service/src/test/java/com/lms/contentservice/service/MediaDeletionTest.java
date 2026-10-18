package com.lms.contentservice.service;

import com.lms.contentservice.client.EnrollmentClient;
import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.AssetType;
import com.lms.contentservice.mapper.MediaAssetMapper;
import com.lms.contentservice.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

class MediaDeletionTest {
    @Test void imageDeletesFromImagesBucketAndVideoAlsoDeletesHlsPrefix() {
        for (AssetType type : new AssetType[]{AssetType.IMAGE, AssetType.VIDEO}) {
            var repository = mock(MediaAssetRepository.class); var storage = mock(StorageService.class);
            var service = new MediaService(mock(ApplicationEventPublisher.class), repository, storage,
                    mock(VideoProcessingService.class), mock(EnrollmentClient.class), mock(MediaAssetMapper.class));
            ReflectionTestUtils.setField(service, "rawBucket", "raw");
            ReflectionTestUtils.setField(service, "hlsBucket", "hls");
            ReflectionTestUtils.setField(service, "imagesBucket", "images");
            var id = UUID.randomUUID(); var owner = UUID.randomUUID();
            var asset = MediaAsset.builder().id(id).ownerId(owner).type(type).rawStorageKey("original").build();
            if (type == AssetType.VIDEO) asset.setHlsMasterKey("hls/test/master.m3u8");
            when(repository.findById(id)).thenReturn(Optional.of(asset));
            service.deleteMedia(id, owner, "INSTRUCTOR");
            verify(storage).delete(type == AssetType.IMAGE ? "images" : "raw", "original");
            if (type == AssetType.VIDEO) verify(storage).deletePrefix("hls", "hls/test/");
            verify(repository).delete(asset);
        }
    }
}
