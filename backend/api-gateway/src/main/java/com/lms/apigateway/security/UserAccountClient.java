package com.lms.apigateway.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.UUID;

@Component
public class UserAccountClient {
    private final RestClient client;

    public UserAccountClient(@Value("${services.user.base-url}") String baseUrl,
                             @Value("${internal.api-key:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Internal-Service-Key", key).build();
    }

    public boolean isActive(UUID userId, String role) {
        try {
            Envelope result = client.get().uri("/api/v1/internal/users/{id}", userId)
                    .retrieve().body(Envelope.class);
            return result != null && result.success() && result.data() != null
                    && userId.equals(result.data().id()) && role.equals(result.data().role())
                    && ("ACTIVE".equals(result.data().status())
                        || "PENDING_VERIFICATION".equals(result.data().status()));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return false;
            throw ex;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(boolean success, Account data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(UUID id, String role, String status) {}
}
