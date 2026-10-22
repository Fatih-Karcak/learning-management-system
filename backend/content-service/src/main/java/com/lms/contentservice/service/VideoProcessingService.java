package com.lms.contentservice.service;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.MediaStatus;
import com.lms.contentservice.exception.MediaNotFoundException;
import com.lms.contentservice.exception.StorageException;
import com.lms.contentservice.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final StorageService storageService;
    private final MediaAssetRepository mediaAssetRepository;

    @Value("${minio.bucket.raw}")    private String rawBucket;
    @Value("${minio.bucket.hls}")    private String hlsBucket;
    @Value("${minio.bucket.images}") private String imagesBucket;

    @Async("videoProcessingExecutor")
    public void processVideoAsync(UUID mediaId) {
        Path tempDir = null;
        try {
            MediaAsset asset = mediaAssetRepository.findById(mediaId)
                    .orElseThrow(() -> new MediaNotFoundException("Medya bulunamadı: " + mediaId));

            tempDir = Files.createTempDirectory("lms-video-" + mediaId);
            Path inputFile = tempDir.resolve("input" + extractExtension(asset.getOriginalFileName()));

            // 1) Ham dosyayı MinIO'dan indir
            try (InputStream in = storageService.download(rawBucket, asset.getRawStorageKey());
                 OutputStream out = Files.newOutputStream(inputFile)) {
                in.transferTo(out);
            }

            // 2) Süreyi öğren
            int duration = probeDurationSeconds(inputFile);

            // 3) Thumbnail çıkar (1. saniyeden bir kare)
            Path thumbFile = tempDir.resolve("thumbnail.jpg");
            runProcess(List.of("ffmpeg", "-y", "-i", inputFile.toString(),
                    "-ss", "00:00:01", "-vframes", "1", thumbFile.toString()));

            String thumbKey = "thumbnails/" + mediaId + ".jpg";
            storageService.uploadLocalFile(imagesBucket, thumbKey, thumbFile, "image/jpeg");

            // 4) HLS segmentlerine böl
            Path hlsDir = Files.createDirectory(tempDir.resolve("hls"));
            Path playlist = hlsDir.resolve("master.m3u8");
            runProcess(List.of("ffmpeg", "-y", "-i", inputFile.toString(),
                    "-codec:", "copy",
                    "-start_number", "0",
                    "-hls_time", "10",
                    "-hls_list_size", "0",
                    "-f", "hls", playlist.toString()));

            // 5) Üretilen .ts + .m3u8 dosyalarını MinIO'ya yükle
            String hlsPrefix = "hls/" + mediaId + "/";
            try (var files = Files.list(hlsDir)) {
                for (Path f : files.toList()) {
                    String contentType = f.toString().endsWith(".m3u8")
                            ? "application/vnd.apple.mpegurl" : "video/mp2t";
                    storageService.uploadLocalFile(hlsBucket, hlsPrefix + f.getFileName(), f, contentType);
                }
            }

            // 6) Durumu güncelle
            asset.setStatus(MediaStatus.READY);
            asset.setDurationSeconds(duration);
            asset.setHlsMasterKey(hlsPrefix + "master.m3u8");
            asset.setThumbnailKey(thumbKey);
            mediaAssetRepository.save(asset);

            log.info("Video işleme tamamlandı: {}", mediaId);

        } catch (Exception e) {
            log.error("Video işleme hatası - mediaId: {}, hata: {}", mediaId, e.getMessage(), e);
            mediaAssetRepository.findById(mediaId).ifPresent(asset -> {
                asset.setStatus(MediaStatus.FAILED);
                asset.setFailureReason(e.getMessage());
                mediaAssetRepository.save(asset);
            });
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private int probeDurationSeconds(Path inputFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "csv=p=0", inputFile.toString())
                .redirectErrorStream(true)
                .start();

        String output;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
            reader.lines().forEach(l -> { /* kalan çıktı tüketiliyor, process kilitlenmesin */ });
        }
        process.waitFor();

        try {
            return (int) Double.parseDouble(output.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void runProcess(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(line -> log.debug("[ffmpeg] {}", line));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new StorageException("İşlem başarısız (exit=" + exitCode + "): " + command);
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".mp4";
    }

    private void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            log.warn("Geçici dizin temizlenemedi: {}", dir);
        }
    }
}