package com.lms.contentservice.service;

import com.lms.contentservice.domain.entity.MediaAsset;
import com.lms.contentservice.domain.enums.MediaStatus;
import com.lms.contentservice.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real encoder/decoder check. Run with -Dlms.ffmpeg.tests=true and FFmpeg on PATH. */
@EnabledIfSystemProperty(named = "lms.ffmpeg.tests", matches = "true")
class VideoProcessingTest {
    @TempDir Path temp;
    final StorageService storage = mock(StorageService.class);
    final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    final VideoProcessingService processor = new VideoProcessingService(storage, repository);
    final Map<String, byte[]> uploaded = new HashMap<>();

    private UUID process(Path source) throws Exception {
        UUID id = UUID.randomUUID();
        var asset = MediaAsset.builder().id(id).originalFileName(source.getFileName().toString())
                .rawStorageKey("raw/source").status(MediaStatus.PROCESSING).build();
        when(repository.findById(id)).thenReturn(Optional.of(asset));
        ReflectionTestUtils.setField(processor, "rawBucket", "raw");
        ReflectionTestUtils.setField(processor, "hlsBucket", "hls");
        ReflectionTestUtils.setField(processor, "imagesBucket", "images");
        when(storage.download("raw", "raw/source")).thenAnswer(call -> Files.newInputStream(source));
        doAnswer(call -> {
            uploaded.put(call.getArgument(1), Files.readAllBytes(call.getArgument(2, Path.class)));
            return null;
        }).when(storage).uploadLocalFile(anyString(), anyString(), any(Path.class), anyString());
        processor.processVideoAsync(id);
        return id;
    }

    @Test void transcodesIncompatibleVideoAndAudioToBrowserCompatibleHls() throws Exception {
        Path source = temp.resolve("source.mkv");
        run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x180:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "2", "-c:v", "mpeg4", "-c:a", "pcm_s16le", source.toString());
        UUID id = process(source);
        verify(repository).completeProcessing(eq(id), eq(MediaStatus.READY), intThat(d -> d >= 2),
                eq("hls/" + id + "/master.m3u8"), eq("thumbnails/" + id + ".jpg"));
        verify(repository, never()).failProcessing(any(), any(), anyString());
        assertTrue(new String(uploaded.get("hls/" + id + "/master.m3u8")).contains("#EXT-X-ENDLIST"));
        Path segment = temp.resolve("segment.ts");
        Files.write(segment, uploaded.get("hls/" + id + "/master0.ts"));
        String codecs = run("ffprobe", "-v", "error", "-show_entries", "stream=codec_name,pix_fmt", "-of", "csv=p=0", segment.toString());
        assertTrue(codecs.contains("h264,yuv420p"), codecs);
        assertTrue(codecs.contains("aac"), codecs);
        // Decoding the entire output catches malformed segments that merely report the right codec.
        run("ffmpeg", "-v", "error", "-i", segment.toString(), "-f", "null", "-");
    }

    @Test void silentSubsecondVideoHasThumbnailAndPositiveDuration() throws Exception {
        Path source = temp.resolve("short.mp4");
        run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=160x90:rate=24",
                "-t", "0.25", "-an", "-c:v", "mpeg4", source.toString());
        UUID id = process(source);
        verify(repository).completeProcessing(eq(id), eq(MediaStatus.READY), eq(1), anyString(), anyString());
        assertTrue(uploaded.get("thumbnails/" + id + ".jpg").length > 0);
    }

    @Test void corruptUploadFailsWithoutPublishingPlayableOutput() throws Exception {
        Path source = temp.resolve("broken.mp4");
        Files.writeString(source, "not a video");
        UUID id = process(source);
        verify(repository).failProcessing(eq(id), eq(MediaStatus.FAILED), contains("Video bilgileri okunamadı"));
        verify(repository, never()).completeProcessing(any(), any(), anyInt(), anyString(), anyString());
        assertTrue(uploaded.isEmpty());
    }

    private String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
