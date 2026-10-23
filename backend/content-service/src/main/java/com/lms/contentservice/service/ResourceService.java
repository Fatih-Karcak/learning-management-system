package com.lms.contentservice.service;

import com.lms.contentservice.client.EnrollmentClient;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final CourseIdentityClient courses;
    private final EnrollmentClient enrollments;

    @Value("${minio.bucket.raw}")
    private String bucket;

    @Value("${content.scan.required:false}")
    private boolean scanRequired;

    @Value("${content.scan.host:clamav}")
    private String scanHost;

    public record Resource(
        UUID id,
        UUID ownerId,
        UUID courseId,
        String purpose,
        String name,
        String contentType,
        long sizeBytes,
        String url
    ) {}

    public record Download(Resource resource, InputStream stream) {}

    public Resource metadata(UUID id) {
        var rows = jdbc.queryForList("select * from learning_resources where id=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var r = rows.getFirst();
        return new Resource(
            id,
            (UUID) r.get("owner_id"),
            (UUID) r.get("course_id"),
            (String) r.get("purpose"),
            (String) r.get("name"),
            (String) r.get("content_type"),
            ((Number) r.get("size_bytes")).longValue(),
            "/api/v1/media/resources/" + id
        );
    }

    public static String validate(byte[] data, String name) {
        if (data.length == 0 || data.length > 25 * 1024 * 1024) throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Dosya 25 MB sınırını aşıyor veya boş"
        );
        String lower = name.toLowerCase(Locale.ROOT);
        if (
            lower.endsWith(".pdf") &&
            data.length >= 5 &&
            new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
        ) return "application/pdf";
        if (lower.endsWith(".txt") || lower.endsWith(".vtt")) {
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException("Metin UTF-8 olmalı");
            }
            if (text.indexOf(0) >= 0) throw new IllegalArgumentException("Metin dosyası geçersiz");
            if (lower.endsWith(".vtt")) {
                if (data.length > 1024 * 1024) throw new IllegalArgumentException(
                    "Altyazı en fazla 1 MB olabilir"
                );
                if (!text.stripLeading().startsWith("WEBVTT")) throw new IllegalArgumentException(
                    "Geçerli WebVTT altyazısı gerekli"
                );
                return "text/vtt";
            }
            return "text/plain";
        }
        throw new ResponseStatusException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Yalnızca PDF, UTF-8 TXT ve WebVTT kabul edilir"
        );
    }

    private void scan(byte[] data) {
        if (!scanRequired) return;
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(scanHost, 3310), 3000);
            socket.setSoTimeout(30000);
            var out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < data.length; i += 8192) {
                int count = Math.min(8192, data.length - i);
                out.writeInt(count);
                out.write(data, i, count);
            }
            out.writeInt(0);
            out.flush();
            var response = new ByteArrayOutputStream();
            for (int count = 0; count < 256; count++) {
                int value = socket.getInputStream().read();
                if (value <= 0) break;
                response.write(value);
            }
            String status = response.toString(StandardCharsets.UTF_8);
            if (!status.startsWith("stream: OK")) throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Dosya güvenlik taramasından geçemedi"
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Dosya tarama servisi kullanılamıyor"
            );
        }
    }

    @Transactional
    public Resource upload(MultipartFile file, UUID course, String purpose, UUID user, String role)
        throws IOException {
        if (!Set.of("MATERIAL", "ASSIGNMENT").contains(purpose)) throw new IllegalArgumentException(
            "Dosya amacı geçersiz"
        );
        boolean owner = courses.owner(course, user);
        if (purpose.equals("MATERIAL") && !owner) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Yalnızca kurs eğitmeni kaynak ekleyebilir"
        );
        if (
            purpose.equals("ASSIGNMENT") &&
            (owner || !Boolean.TRUE.equals(enrollments.checkEnrollment(course, user).data()))
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ödev dosyası için öğrenci kaydı gerekli");
        if (file.getSize() > 25 * 1024 * 1024) throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Dosya en fazla 25 MB olabilir"
        );
        String name =
            file.getOriginalFilename() == null
                ? "file"
                : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        byte[] data = file.getBytes();
        String type = validate(data, name);
        scan(data);
        UUID id = UUID.randomUUID();
        String key = "resources/" + id + "/" + name;
        storage.upload(bucket, key, new ByteArrayInputStream(data), data.length, type);
        jdbc.update(
            "insert into learning_resources(id,owner_id,course_id,purpose,name,content_type,size_bytes,storage_key) values(?,?,?,?,?,?,?,?)",
            id,
            user,
            course,
            purpose,
            name,
            type,
            data.length,
            key
        );
        return metadata(id);
    }

    public Download download(UUID id, UUID user, String role) {
        var resource = metadata(id);
        boolean allowed = user.equals(resource.ownerId()) || "ADMIN".equals(role);
        if (!allowed) allowed = "MATERIAL".equals(resource.purpose())
            ? courses.owner(resource.courseId(), user) ||
              Boolean.TRUE.equals(enrollments.checkEnrollment(resource.courseId(), user).data())
            : courses.owner(resource.courseId(), user);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dosyaya erişim yetkiniz yok");
        String key = jdbc.queryForObject(
            "select storage_key from learning_resources where id=?",
            String.class,
            id
        );
        return new Download(resource, storage.download(bucket, key));
    }
}
