package com.lms.contentservice.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ResourceValidationTest {

    @Test
    void rejectsRenamedExecutablesAndActiveWebFiles() {
        assertThrows(ResponseStatusException.class, () ->
            ResourceService.validate("MZ executable".getBytes(), "renamed.pdf")
        );
        assertThrows(ResponseStatusException.class, () ->
            ResourceService.validate("<svg onload='alert(1)'/>".getBytes(), "image.svg")
        );
        assertThrows(ResponseStatusException.class, () ->
            ResourceService.validate("<script/>".getBytes(), "lesson.html")
        );
    }

    @Test
    void requiresUtf8AndAValidWebVttHeader() {
        assertThrows(IllegalArgumentException.class, () ->
            ResourceService.validate(new byte[] { (byte) 0xff, (byte) 0xfe }, "lesson.txt")
        );
        assertThrows(IllegalArgumentException.class, () ->
            ResourceService.validate("No header".getBytes(), "captions.vtt")
        );
        assertEquals(
            "text/vtt",
            ResourceService.validate(
                "WEBVTT\n\n00:00.000 --> 00:01.000\nHello".getBytes(StandardCharsets.UTF_8),
                "captions.vtt"
            )
        );
    }

    @Test
    void permitsPdfAndPlainTextAndRejectsEmptyFiles() {
        assertEquals(
            "application/pdf",
            ResourceService.validate("%PDF-1.7 content".getBytes(), "project.pdf")
        );
        assertEquals(
            "text/plain",
            ResourceService.validate("Ödev".getBytes(StandardCharsets.UTF_8), "project.txt")
        );
        assertThrows(ResponseStatusException.class, () -> ResourceService.validate(new byte[0], "empty.txt"));
    }
}
