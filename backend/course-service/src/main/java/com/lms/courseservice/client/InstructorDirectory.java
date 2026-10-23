package com.lms.courseservice.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InstructorDirectory {

    private final RestClient client;

    public record Instructor(UUID id, String displayName) {}

    public record Envelope(boolean success, Instructor data) {}

    public record Account(String role, String status, boolean emailVerified) {}

    public record AccountEnvelope(boolean success, Account data) {}

    public boolean active(UUID user) {
        var result = client
            .get()
            .uri("/api/v1/internal/users/{id}", user)
            .retrieve()
            .body(AccountEnvelope.class);
        return (
            result != null &&
            result.success() &&
            result.data() != null &&
            "INSTRUCTOR".equals(result.data().role()) &&
            "ACTIVE".equals(result.data().status()) &&
            result.data().emailVerified()
        );
    }

    public InstructorDirectory(
        @Value("${services.user.base-url:http://localhost:8081}") String url,
        @Value("${internal.api-key:}") String key
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        client = RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeader("X-Internal-Service-Key", key)
            .build();
    }

    public Instructor find(String email) {
        try {
            var result = client
                .get()
                .uri("/api/v1/internal/users/instructor?email={email}", email)
                .retrieve()
                .body(Envelope.class);
            if (
                result == null || !result.success() || result.data() == null
            ) throw new IllegalStateException("Eğitmen doğrulanamadı");
            return result.data();
        } catch (
            org.springframework.web.client.RestClientResponseException ex
        ) {
            if (
                ex.getStatusCode().value() == 404
            ) throw new IllegalArgumentException(
                "Bu e-posta adresiyle kayıtlı aktif eğitmen bulunamadı"
            );
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Eğitmen bilgisi doğrulanamadı"
            );
        }
    }
}
