package com.lms.paymentservice.client;

import com.lms.paymentservice.config.HttpClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CourseClientContractTest {
    @Test void bindsCourseIdAndUnwrapsActualCourseApiEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/courses/" + id, exchange -> {
            byte[] body = ("{\"success\":true,\"message\":\"OK\",\"data\":{\"id\":\"" + id
                    + "\",\"title\":\"Course\",\"price\":100,\"discountPrice\":80,\"status\":\"PUBLISHED\",\"sections\":[]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var config = new HttpClientConfig();
            ReflectionTestUtils.setField(config, "courseServiceUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            var result = config.courseClient(WebClient.builder()).getCoursePrice(id);
            assertTrue(result.success()); assertEquals(id, result.data().id());
            assertEquals(0, new BigDecimal("80").compareTo(result.data().effectivePrice()));
            assertEquals("PUBLISHED", result.data().status());
        } finally { server.stop(0); }
    }
}
