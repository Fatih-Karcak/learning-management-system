package com.lms.contentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ResumableUploadTest {
  final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  final StorageService storage = mock(StorageService.class);
  final CourseIdentityClient courses = mock(CourseIdentityClient.class);
  final MediaService media = mock(MediaService.class);
  final ResumableUploadService service = new ResumableUploadService(jdbc, storage, courses, media);
  final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();

  ResumableUploadTest() {
    ReflectionTestUtils.setField(service, "bucket", "raw");
  }

  void session(long size, long received) {
    when(jdbc.queryForList(
            "select * from video_uploads where id=? and owner_id=? for update", id, owner))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    id,
                    "status",
                    "OPEN",
                    "total_bytes",
                    size,
                    "received_bytes",
                    received,
                    "expires_at",
                    Timestamp.valueOf(LocalDateTime.now().plusDays(1)))));
  }

  @Test
  void wrongOwnerCannotReadOrModifyUploads() {
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.status(id, owner))
            .getStatusCode()
            .value());
    assertEquals(
        404,
        assertThrows(
                ResponseStatusException.class,
                () -> service.put(id, 0, new byte[] {1}, "0".repeat(64), owner))
            .getStatusCode()
            .value());
    verifyNoInteractions(storage, media);
  }

  @Test
  void invalidChecksumAndChunkBoundariesNeverWriteToStorage() {
    session(3, 0);
    byte[] bytes = {1, 2, 3};
    assertThrows(
        ResponseStatusException.class, () -> service.put(id, 0, bytes, "0".repeat(64), owner));
    assertThrows(
        ResponseStatusException.class,
        () -> service.put(id, 1, bytes, ResumableUploadService.checksum(bytes), owner));
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.put(
                id, 0, new byte[] {1}, ResumableUploadService.checksum(new byte[] {1}), owner));
    verifyNoInteractions(storage, media);
  }

  @Test
  void incompleteUploadsCannotCreatePlayableMedia() {
    session(3, 2);
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> service.complete(id, owner))
            .getStatusCode()
            .value());
    verifyNoInteractions(storage, media);
  }

  @Test
  void aRetriedChunkWithDifferentBytesCannotOverwriteAcceptedData() {
    session(3, 3);
    byte[] bytes = {1, 2, 3};
    when(jdbc.queryForList(
            "select sha256 from video_upload_parts where upload_id=? and part_index=?",
            String.class,
            id,
            0))
        .thenReturn(List.of("0".repeat(64)));
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.put(id, 0, bytes, ResumableUploadService.checksum(bytes), owner))
            .getStatusCode()
            .value());
    verifyNoInteractions(storage, media);
  }

  @Test
  void ownersCanCancelEvenAfterCourseEditingAccessIsRevoked() {
    session(3, 0);
    service.cancel(id, owner);
    verify(jdbc)
        .update("update video_uploads set status='CANCELLED',expires_at=now() where id=?", id);
    verifyNoInteractions(courses, storage, media);
  }
}
