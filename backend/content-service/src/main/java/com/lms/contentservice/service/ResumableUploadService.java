package com.lms.contentservice.service;

import jakarta.validation.constraints.*;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ResumableUploadService {
  public static final int CHUNK_BYTES = 8 * 1024 * 1024;
  private final JdbcTemplate jdbc;
  private final StorageService storage;
  private final CourseIdentityClient courses;
  private final MediaService media;

  @Value("${minio.bucket.raw}")
  private String bucket;

  public record Start(
      @NotBlank @Size(max = 200) String fileName,
      @NotBlank @Pattern(regexp = "video/[a-zA-Z0-9.+-]+") @Size(max = 100) String contentType,
      @Min(1) @Max(2147483648L) long totalBytes,
      UUID courseId) {}

  public record Part(int index, int size, String sha256) {}

  public record Upload(
      UUID id,
      String status,
      long totalBytes,
      long receivedBytes,
      int chunkBytes,
      Instant expiresAt,
      List<Part> parts) {}

  public List<Map<String, Object>> pending(UUID owner) {
    return jdbc.queryForList(
        "select id,file_name,course_id,total_bytes,received_bytes,expires_at from video_uploads"
            + " where owner_id=? and status='OPEN' and expires_at>now() order by updated_at desc"
            + " limit 5",
        owner);
  }

  @Transactional
  public Upload start(Start input, UUID owner) {
    // Serialize per-owner quota checks, including concurrent requests on different app instances.
    jdbc.queryForList("select pg_advisory_xact_lock(?)", owner.getMostSignificantBits());
    editor(input.courseId(), owner);
    int active =
        jdbc.queryForObject(
            "select count(*) from video_uploads where owner_id=? and status='OPEN' and"
                + " expires_at>now()",
            Integer.class,
            owner);
    if (active >= 5)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Finish or cancel an existing upload first");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " video_uploads(id,owner_id,course_id,file_name,content_type,total_bytes,expires_at)"
            + " values(?,?,?,?,?,?,?)",
        id,
        owner,
        input.courseId(),
        input.fileName(),
        input.contentType(),
        input.totalBytes(),
        LocalDateTime.now(Clock.systemUTC()).plusDays(7));
    return status(id, owner);
  }

  private void editor(UUID course, UUID owner) {
    if (course != null && !courses.canEdit(course, owner))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot upload to this course");
  }

  private Map<String, Object> require(UUID id, UUID owner, boolean lock) {
    var rows =
        jdbc.queryForList(
            "select * from video_uploads where id=? and owner_id=?" + (lock ? " for update" : ""),
            id,
            owner);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found");
    var row = rows.getFirst();
    if (!"COMPLETED".equals(row.get("status"))
        && ((java.sql.Timestamp) row.get("expires_at"))
            .toLocalDateTime()
            .isBefore(LocalDateTime.now(Clock.systemUTC())))
      throw new ResponseStatusException(HttpStatus.GONE, "Upload expired; start a new upload");
    editor((UUID) row.get("course_id"), owner);
    return row;
  }

  public Upload status(UUID id, UUID owner) {
    var row = require(id, owner, false);
    var parts =
        jdbc.query(
            "select part_index,size_bytes,sha256 from video_upload_parts where upload_id=? order by"
                + " part_index",
            (rs, n) -> new Part(rs.getInt(1), rs.getInt(2), rs.getString(3)),
            id);
    return new Upload(
        id,
        (String) row.get("status"),
        ((Number) row.get("total_bytes")).longValue(),
        ((Number) row.get("received_bytes")).longValue(),
        CHUNK_BYTES,
        ((java.sql.Timestamp) row.get("expires_at")).toLocalDateTime().toInstant(ZoneOffset.UTC),
        parts);
  }

  public static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Upload put(UUID id, int index, byte[] bytes, String checksum, UUID owner) {
    var row = require(id, owner, true);
    if (!"OPEN".equals(row.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is not open");
    long total = ((Number) row.get("total_bytes")).longValue();
    long offset = (long) index * CHUNK_BYTES;
    if (index < 0 || offset >= total || bytes.length != Math.min(CHUNK_BYTES, total - offset))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid chunk index or size");
    if (checksum == null
        || !checksum.matches("[0-9a-f]{64}")
        || !MessageDigest.isEqual(
            checksum.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            checksum(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk checksum does not match");
    var existing =
        jdbc.queryForList(
            "select sha256 from video_upload_parts where upload_id=? and part_index=?",
            String.class,
            id,
            index);
    if (!existing.isEmpty()) {
      if (!checksum.equals(existing.getFirst()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "A different chunk already exists at this position");
      return status(id, owner);
    }
    if (((Number) row.get("received_bytes")).longValue() != offset)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Resume from the confirmed upload offset");
    String key = "uploads/" + id + "/" + index;
    storage.upload(
        bucket, key, new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream");
    jdbc.update(
        "insert into video_upload_parts(upload_id,part_index,size_bytes,sha256,storage_key)"
            + " values(?,?,?,?,?)",
        id,
        index,
        bytes.length,
        checksum,
        key);
    jdbc.update(
        "update video_uploads set received_bytes=received_bytes+?,updated_at=now() where id=?",
        bytes.length,
        id);
    return status(id, owner);
  }

  @Transactional
  public com.lms.contentservice.dto.response.MediaAssetResponse complete(UUID id, UUID owner) {
    var row = require(id, owner, true);
    if ("CANCELLED".equals(row.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload was cancelled");
    if (!row.get("received_bytes").equals(row.get("total_bytes")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is incomplete");
    String rawKey = "raw/" + id + "/source";
    if (!"COMPLETED".equals(row.get("status"))) {
      var parts =
          jdbc.queryForList(
              "select storage_key from video_upload_parts where upload_id=? order by part_index",
              String.class,
              id);
      storage.compose(bucket, rawKey, parts);
    }
    var result =
        media.registerUploadedVideo(
            id,
            owner,
            (UUID) row.get("course_id"),
            (String) row.get("file_name"),
            (String) row.get("content_type"),
            ((Number) row.get("total_bytes")).longValue(),
            rawKey);
    jdbc.update("update video_uploads set status='COMPLETED',updated_at=now() where id=?", id);
    return result;
  }

  @Transactional
  public void cancel(UUID id, UUID owner) {
    // Owners can discard their uploads even after losing course editing access.
    var rows =
        jdbc.queryForList(
            "select * from video_uploads where id=? and owner_id=? for update", id, owner);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found");
    var row = rows.getFirst();
    if (!"OPEN".equals(row.get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is not open");
    jdbc.update("update video_uploads set status='CANCELLED',expires_at=now() where id=?", id);
  }

  @Scheduled(fixedDelayString = "${content.upload.cleanup-ms:3600000}")
  @Transactional
  public void cleanup() {
    for (var row :
        jdbc.queryForList(
            "select id,status from video_uploads where (expires_at<now() and status<>'COMPLETED')"
                + " or (status='COMPLETED' and updated_at<now()-interval '1 hour') order by"
                + " updated_at limit 20 for update skip locked")) {
      UUID id = (UUID) row.get("id");
      storage.deletePrefix(bucket, "uploads/" + id + "/");
      if (!"COMPLETED".equals(row.get("status"))) storage.delete(bucket, "raw/" + id + "/source");
      jdbc.update("delete from video_uploads where id=?", id);
    }
  }
}
