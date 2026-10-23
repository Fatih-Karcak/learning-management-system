package com.lms.contentservice.service;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.MediaStatus;
import com.lms.contentservice.exception.MediaNotFoundException;
import com.lms.contentservice.exception.StorageException;
import com.lms.contentservice.repository.MediaAssetRepository;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final StorageService storageService;
    private final MediaAssetRepository mediaAssetRepository;
    private final VideoJobLock jobs;

    @Value("${minio.bucket.raw}")
    private String rawBucket;

    @Value("${minio.bucket.hls}")
    private String hlsBucket;

    @Value("${minio.bucket.images}")
    private String imagesBucket;

    @Value("${video.ffmpeg:ffmpeg}")
    private String ffmpeg = "ffmpeg";

    @Value("${video.ffprobe:ffprobe}")
    private String ffprobe = "ffprobe";

    @Async("videoProcessingExecutor")
    public void processVideoAsync(UUID mediaId) {
        Path tempDir = null;
        VideoJobLock.Lease lease = null;
        try {
            lease = jobs.acquire(mediaId);
            if (lease == null) return;
            MediaAsset asset = mediaAssetRepository
                .findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Medya bulunamadı: " + mediaId));

            if (asset.getStatus() != MediaStatus.PROCESSING) return;
            tempDir = Files.createTempDirectory("lms-video-" + mediaId);
            Path inputFile = tempDir.resolve("input" + extractExtension(asset.getOriginalFileName()));

            // 1) Ham dosyayı MinIO'dan indir
            try (
                InputStream in = storageService.download(rawBucket, asset.getRawStorageKey());
                OutputStream out = Files.newOutputStream(inputFile)
            ) {
                in.transferTo(out);
            }

            // 2) Süreyi öğren
            int duration = probeDurationSeconds(inputFile);

            // The first frame also exists in videos shorter than one second.
            Path thumbFile = tempDir.resolve("thumbnail.jpg");
            runProcess(
                List.of(
                    ffmpeg,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-i",
                    inputFile.toString(),
                    "-map",
                    "0:v:0",
                    "-frames:v",
                    "1",
                    "-threads",
                    "1",
                    thumbFile.toString()
                )
            );

            String thumbKey = "thumbnails/" + mediaId + ".jpg";
            storageService.uploadLocalFile(imagesBucket, thumbKey, thumbFile, "image/jpeg");

            // 4) HLS segmentlerine böl
            Path hlsDir = Files.createDirectory(tempDir.resolve("hls"));
            Path playlist = hlsDir.resolve("master.m3u8");
            // Copying source codecs can mark MPEG-4/HEVC videos READY although browsers cannot decode them.
            runProcess(
                List.of(
                    ffmpeg,
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-i",
                    inputFile.toString(),
                    "-map",
                    "0:v:0",
                    "-map",
                    "0:a:0?",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "veryfast",
                    "-crf",
                    "23",
                    "-threads",
                    "2",
                    "-pix_fmt",
                    "yuv420p",
                    "-vf",
                    "scale=ceil(iw/2)*2:ceil(ih/2)*2",
                    "-force_key_frames",
                    "expr:gte(t,n_forced*6)",
                    "-c:a",
                    "aac",
                    "-b:a",
                    "128k",
                    "-ac",
                    "2",
                    "-ar",
                    "48000",
                    "-sn",
                    "-dn",
                    "-start_number",
                    "0",
                    "-hls_time",
                    "6",
                    "-hls_playlist_type",
                    "vod",
                    "-hls_flags",
                    "independent_segments",
                    "-hls_list_size",
                    "0",
                    "-f",
                    "hls",
                    playlist.toString()
                )
            );

            // 5) Üretilen .ts + .m3u8 dosyalarını MinIO'ya yükle
            String hlsPrefix = "hls/" + mediaId + "/";
            try (var files = Files.list(hlsDir)) {
                for (Path f : files.toList()) {
                    String contentType = f.toString().endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl"
                        : "video/mp2t";
                    storageService.uploadLocalFile(hlsBucket, hlsPrefix + f.getFileName(), f, contentType);
                }
            }

            // 6) Durumu güncelle
            mediaAssetRepository.completeProcessing(
                mediaId,
                MediaStatus.READY,
                duration,
                hlsPrefix + "master.m3u8",
                thumbKey
            );

            log.info("Video işleme tamamlandı: {}", mediaId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Video işleme hatası - mediaId: {}, hata: {}", mediaId, e.getMessage(), e);
            String reason = e.getMessage() == null ? "Video işlenemedi" : e.getMessage();
            mediaAssetRepository.failProcessing(
                mediaId,
                MediaStatus.FAILED,
                reason.substring(0, Math.min(reason.length(), 1000))
            );
        } finally {
            if (lease != null) lease.close();
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private int probeDurationSeconds(Path inputFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
            ffprobe,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "csv=p=0",
            inputFile.toString()
        )
            .redirectErrorStream(true)
            .start();

        String output;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.readLine();
            reader.lines().forEach(l -> {
                /* kalan çıktı tüketiliyor, process kilitlenmesin */
            });
        }
        if (process.waitFor() != 0) throw new StorageException(
            "Video bilgileri okunamadı; dosyayı kontrol edin"
        );

        try {
            double seconds = Double.parseDouble(output.trim());
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return (int) Math.ceil(seconds);
        } catch (Exception e) {
            throw new StorageException("Video süresi okunamadı; dosyayı kontrol edin");
        }
    }

    private void runProcess(List<String> command) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new StorageException(
                "FFmpeg başlatılamadı. FFmpeg kurulumunu ve VIDEO_FFMPEG yolunu kontrol edin"
            );
        }

        StringBuilder diagnostics = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    synchronized (diagnostics) {
                        diagnostics.append(line).append('\n');
                        if (diagnostics.length() > 2000) diagnostics.delete(0, diagnostics.length() - 2000);
                    }
                }
            } catch (IOException ignored) {}
        });
        try {
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new StorageException("Video işleme 30 dakika sınırını aştı");
            }
            reader.join(5000);
            if (process.exitValue() != 0) throw new StorageException(
                "Video dönüştürülemedi (exit=" + process.exitValue() + "): " + diagnostics
            );
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            reader.interrupt();
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return ".mp4";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".mp4";
    }

    private void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.warn("Geçici dizin temizlenemedi: {}", dir);
        }
    }
}
