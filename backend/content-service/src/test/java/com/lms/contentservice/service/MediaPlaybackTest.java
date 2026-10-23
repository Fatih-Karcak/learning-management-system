package com.lms.contentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.contentservice.client.EnrollmentClient;
import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.AssetType;
import com.lms.contentservice.domain.enums.MediaStatus;
import com.lms.contentservice.dto.response.EnrollmentCheckResult;
import com.lms.contentservice.exception.MediaNotFoundException;
import com.lms.contentservice.exception.UnauthorizedMediaAccessException;
import com.lms.contentservice.mapper.MediaAssetMapper;
import com.lms.contentservice.repository.MediaAssetRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class MediaPlaybackTest {

    final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    final StorageService storage = mock(StorageService.class);
    final EnrollmentClient enrollments = mock(EnrollmentClient.class);
    final MediaService service = new MediaService(
        mock(ApplicationEventPublisher.class),
        repository,
        storage,
        mock(VideoProcessingService.class),
        enrollments,
        Mappers.getMapper(MediaAssetMapper.class),
        mock(CourseIdentityClient.class)
    );
    final UUID student = UUID.randomUUID();
    final UUID course = UUID.randomUUID();
    final MediaAsset asset = MediaAsset.builder()
        .id(UUID.randomUUID())
        .ownerId(UUID.randomUUID())
        .type(AssetType.VIDEO)
        .status(MediaStatus.READY)
        .hlsMasterKey("hls/video/master.m3u8")
        .build();

    MediaPlaybackTest() {
        ReflectionTestUtils.setField(service, "hlsBucket", "hls");
        when(repository.findById(asset.getId())).thenReturn(Optional.of(asset));
    }

    @Test
    void linkingLibraryVideoAllowsEnrolledStudentToReadManifestAndSegments() throws Exception {
        assertThrows(UnauthorizedMediaAccessException.class, () ->
            service.getManifestContent(asset.getId(), student, "STUDENT")
        );
        UUID lesson = UUID.randomUUID();
        var response = service.linkVideoToLesson(asset.getId(), course, lesson, asset.getOwnerId());
        assertEquals(course, response.getCourseId());
        assertEquals(lesson, response.getLessonId());
        when(enrollments.checkEnrollment(course, student)).thenReturn(
            new EnrollmentCheckResult(true, "ok", true)
        );
        when(storage.download("hls", "hls/video/master.m3u8")).thenReturn(
            new ByteArrayInputStream("#EXTM3U\nmaster0.ts".getBytes(StandardCharsets.UTF_8))
        );
        when(storage.download("hls", "hls/video/master0.ts")).thenReturn(
            new ByteArrayInputStream(new byte[] { 1, 2, 3 })
        );
        assertTrue(service.getManifestContent(asset.getId(), student, "STUDENT").contains("master0.ts"));
        try (var segment = service.getSegmentStream(asset.getId(), "master0.ts", student, "STUDENT")) {
            assertArrayEquals(new byte[] { 1, 2, 3 }, segment.readAllBytes());
        }
    }

    @Test
    void nonEnrolledOrRefundedStudentCannotReadAnyVideoBytes() {
        asset.setCourseId(course);
        when(enrollments.checkEnrollment(course, student)).thenReturn(
            new EnrollmentCheckResult(true, "ok", false)
        );
        assertThrows(UnauthorizedMediaAccessException.class, () ->
            service.getManifestContent(asset.getId(), student, "STUDENT")
        );
        assertThrows(UnauthorizedMediaAccessException.class, () ->
            service.getSegmentStream(asset.getId(), "master0.ts", student, "STUDENT")
        );
        verifyNoInteractions(storage);
    }

    @Test
    void linkRejectsOtherOwnersFailedMediaAndMovingVideoToAnotherCourse() {
        assertThrows(UnauthorizedMediaAccessException.class, () ->
            service.linkVideoToLesson(asset.getId(), course, UUID.randomUUID(), student)
        );
        asset.setStatus(MediaStatus.FAILED);
        assertThrows(ResponseStatusException.class, () ->
            service.linkVideoToLesson(asset.getId(), course, UUID.randomUUID(), asset.getOwnerId())
        );
        asset.setStatus(MediaStatus.READY);
        asset.setCourseId(UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () ->
            service.linkVideoToLesson(asset.getId(), course, UUID.randomUUID(), asset.getOwnerId())
        );
        verify(repository, never()).save(any());
    }

    @Test
    void linkingDuringProcessingDoesNotExposeAnUnfinishedManifest() {
        asset.setStatus(MediaStatus.PROCESSING);
        service.linkVideoToLesson(asset.getId(), course, UUID.randomUUID(), asset.getOwnerId());
        assertThrows(MediaNotFoundException.class, () ->
            service.getManifestContent(asset.getId(), asset.getOwnerId(), "INSTRUCTOR")
        );
        verifyNoInteractions(storage);
    }
}
