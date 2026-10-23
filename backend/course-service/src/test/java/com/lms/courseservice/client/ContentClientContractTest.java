package com.lms.courseservice.client;

import com.lms.courseservice.config.HttpClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContentClientContractTest {
    @Test void sendsInternalKeyAndUnwrapsFullMediaResponse() throws Exception {
        UUID mediaId = UUID.randomUUID();
        var request = new ContentClient.LinkRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var received = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/media/" + mediaId + "/lesson", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Key"));
            method.set(exchange.getRequestMethod());
            byte[] body = ("{\"success\":true,\"message\":\"OK\",\"timestamp\":\"2026-10-22T12:00:00\",\"data\":{\"id\":\"" + mediaId
                    + "\",\"type\":\"VIDEO\",\"status\":\"READY\",\"originalFileName\":\"lesson.mp4\",\"streamUrl\":\"/api/v1/media/" + mediaId
                    + "/manifest.m3u8\",\"durationSeconds\":12}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new HttpClientConfig().contentClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-internal-key");
            var result = client.linkVideo(mediaId, request);
            assertTrue(result.success());
            assertEquals(12, result.data().durationSeconds());
            assertEquals("PUT", method.get());
            assertEquals("test-internal-key", key.get());
            assertTrue(received.get().contains(request.courseId().toString()));
            assertTrue(received.get().contains(request.lessonId().toString()));
            assertTrue(received.get().contains(request.ownerId().toString()));
        } finally { server.stop(0); }
    }
}
