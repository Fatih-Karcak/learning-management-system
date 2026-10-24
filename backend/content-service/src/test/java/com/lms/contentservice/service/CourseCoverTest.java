package com.lms.contentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.*;
import com.lms.contentservice.exception.MediaNotFoundException;
import com.lms.contentservice.repository.MediaAssetRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CourseCoverTest {
  @Test
  void stableCoverAddressesStillRequireAPublishedCourseReference() {
    var repository = mock(MediaAssetRepository.class);
    var storage = mock(StorageService.class);
    var courses = mock(CourseIdentityClient.class);
    var service =
        new MediaService(
            mock(org.springframework.context.ApplicationEventPublisher.class),
            repository,
            storage,
            mock(VideoProcessingService.class),
            mock(com.lms.contentservice.client.EnrollmentClient.class),
            mock(com.lms.contentservice.mapper.MediaAssetMapper.class),
            courses);
    UUID id = UUID.randomUUID(), course = UUID.randomUUID();
    var asset =
        MediaAsset.builder()
            .id(id)
            .ownerId(UUID.randomUUID())
            .courseId(course)
            .type(AssetType.IMAGE)
            .status(MediaStatus.READY)
            .contentType("image/png")
            .rawStorageKey("images/cover.png")
            .build();
    when(repository.findById(id)).thenReturn(Optional.of(asset));
    ReflectionTestUtils.setField(service, "imagesBucket", "images");
    assertThrows(MediaNotFoundException.class, () -> service.getPublicCover(id, null));
    verifyNoInteractions(storage);
    when(courses.isPublishedCover(course, id, null)).thenReturn(true);
    service.getPublicCover(id, null);
    verify(storage).download("images", "images/cover.png");
  }

  @Test
  void legacySignedCoversAreRecognizedWithoutAcceptingArbitraryUrls() {
    UUID id = UUID.randomUUID();
    assertTrue(
        CourseIdentityClient.referencesCover(
            "https://files.example/lms-images/images/" + id + "/cover.png?X-Amz-Signature=expired",
            id));
    assertFalse(
        CourseIdentityClient.referencesCover(
            "https://files.example/private/" + id + "?X-Amz-Signature=expired", id));
    assertFalse(
        CourseIdentityClient.referencesCover(
            "https://files.example/lms-images/images/"
                + UUID.randomUUID()
                + "/cover.png?X-Amz-Signature=x",
            id));
  }
}
