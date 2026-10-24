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
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));

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

            // Produce a capped adaptive ladder without upscaling small sources.
            Path hlsDir = Files.createDirectory(tempDir.resolve("hls"));
            int sourceHeight = probeDimensions(inputFile)[1];
            if (sourceHeight <= 360) {
                encode(inputFile, hlsDir.resolve("master.m3u8"), 0, hlsDir.resolve("master%d.ts"));
            } else {
                var heights = new java.util.ArrayList<Integer>();
                heights.add(360);
                if (sourceHeight >= 720) heights.add(720);
                if (sourceHeight >= 1080) heights.add(1080);
                if (sourceHeight < 720) heights.add(sourceHeight / 2 * 2);
                StringBuilder master = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-INDEPENDENT-SEGMENTS\n");
                for (int height : heights.stream().distinct().toList()) {
                    String name = "quality-" + height;
                    encode(inputFile, hlsDir.resolve(name+".m3u8"), height, hlsDir.resolve(name+"-%05d.ts"));
                    int[] dimensions = probeDimensions(hlsDir.resolve(name+"-00000.ts"));
                    int bandwidth = height <= 360 ? 1000000 : height <= 720 ? 3000000 : 5500000;
                    master.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                        .append(",RESOLUTION=").append(dimensions[0]).append('x').append(dimensions[1]).append('\n')
                        .append(name).append(".m3u8\n");
                }
                Files.writeString(hlsDir.resolve("master.m3u8"), master.toString());
            }

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

            log.info("Video processing completed: {}", mediaId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Video processing failed - mediaId: {}, error: {}", mediaId, e.getMessage(), e);
            String reason = e.getMessage() == null ? "Video processing failed" : e.getMessage();
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

    private void encode(Path input, Path playlist, int height, Path segmentPattern) throws IOException, InterruptedException {
        runProcess(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", input.toString(),
            "-map", "0:v:0", "-map", "0:a:0?", "-c:v", "libx264", "-preset", "veryfast",
            "-crf", "23", "-maxrate", height <= 360 ? "900k" : height <= 720 ? "2800k" : "5200k",
            "-bufsize", height <= 360 ? "1800k" : height <= 720 ? "5600k" : "10400k",
            "-threads", "2", "-pix_fmt", "yuv420p", "-vf",
            height == 0 ? "scale=ceil(iw/2)*2:ceil(ih/2)*2,setsar=1" : "scale=-2:"+height+",setsar=1",
            "-force_key_frames", "expr:gte(t,n_forced*6)", "-c:a", "aac", "-b:a", "128k",
            "-ac", "2", "-ar", "48000", "-sn", "-dn", "-start_number", "0", "-hls_time", "6",
            "-hls_playlist_type", "vod", "-hls_flags", "independent_segments", "-hls_list_size", "0",
            "-hls_segment_filename", segmentPattern.toString(), "-f", "hls", playlist.toString()));
    }

    private String probe(Path input, String entries, String format, boolean videoOnly) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of(ffprobe,"-v","error"));
        if (videoOnly) command.addAll(List.of("-select_streams","v:0"));
        command.addAll(List.of("-show_entries",entries,"-of",format,input.toString()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new java.io.ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var stream=process.getInputStream()) { stream.transferTo(output); } catch (IOException ignored) {}
        });
        try {
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) throw new StorageException("Video probe timed out");
            reader.join(2000);
            if (process.exitValue()!=0) throw new StorageException("Video details could not be read; check the file");
            return output.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        } finally { if(process.isAlive()) process.destroyForcibly(); reader.interrupt(); }
    }

    private int[] probeDimensions(Path input) throws IOException, InterruptedException {
        String[] values=probe(input,"stream=width,height","csv=s=x:p=0",true).split("[x\\r\\n]+");
        try { return new int[]{Integer.parseInt(values[0]),Integer.parseInt(values[1])}; }
        catch (RuntimeException ex) { throw new StorageException("Video dimensions could not be read"); }
    }

    private int probeDurationSeconds(Path input) throws IOException, InterruptedException {
        try {
            double seconds=Double.parseDouble(probe(input,"format=duration","csv=p=0",false));
            if (!Double.isFinite(seconds)||seconds<=0||seconds>Integer.MAX_VALUE) throw new NumberFormatException();
            return (int)Math.ceil(seconds);
        } catch (NumberFormatException ex) { throw new StorageException("Video duration could not be read"); }
    }

    private void runProcess(List<String> command) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new StorageException(
                "FFmpeg could not start. Check its installation and the VIDEO_FFMPEG path"
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
                throw new StorageException("Video processing exceeded the 30-minute limit");
            }
            reader.join(5000);
            if (process.exitValue() != 0) throw new StorageException(
                "Video conversion failed (exit=" + process.exitValue() + "): " + diagnostics
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
            log.warn("The temporary directory could not be cleaned: {}", dir);
        }
    }
}
