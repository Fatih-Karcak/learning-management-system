package com.lms.contentservice.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CourseIdentityClient {

    private final RestClient client;

    public CourseIdentityClient(
        @Value("${services.course.base-url:http://localhost:8082}") String url,
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

    public record Lesson(UUID id, String videoUrl, boolean previewAllowed) {}

    public record Section(java.util.List<Lesson> lessons) {}

    public record Course(
        UUID id,
        UUID instructorId,
        String status,
        String thumbnailUrl,
        java.util.List<Section> sections
    ) {}

    public record Envelope(boolean success, Course data) {}

    public record Permission(boolean success, Boolean data) {}

    public boolean canLearn(UUID course,UUID media,UUID user) {
        var envelope=client.get().uri("/api/v1/internal/courses/{id}",course).retrieve().body(Envelope.class);
        if(envelope==null||!envelope.success()||envelope.data()==null||envelope.data().sections()==null) return false;
        String expected="/api/v1/media/"+media+"/manifest.m3u8";
        for(var section:envelope.data().sections()) if(section.lessons()!=null) for(var lesson:section.lessons()) {
            if(!expected.equals(lesson.videoUrl())) continue;
            var permission=client.get().uri("/api/v1/internal/courses/{id}/lessons/{lesson}/access?studentId={user}",course,lesson.id(),user)
                .retrieve().body(Permission.class);
            if(permission!=null&&permission.success()&&Boolean.TRUE.equals(permission.data())) return true;
        }
        return false;
    }
    public boolean canReadResource(UUID course,UUID resource,UUID user) {
        var response=client.get().uri("/api/v1/internal/courses/{course}/resources/{resource}/access?studentId={user}",course,resource,user)
            .retrieve().body(Permission.class);
        return response!=null&&response.success()&&Boolean.TRUE.equals(response.data());
    }

    public boolean isPublishedCover(UUID course, UUID media) {
        return isPublishedCover(course,media,null);
    }
    public boolean isPublishedCover(UUID course,UUID media,UUID unboundOwner) {
        var response = client.get().uri("/api/v1/internal/courses/{id}", course).retrieve().body(Envelope.class);
        return response != null && response.success() && response.data() != null &&
            Set.of("PUBLISHED", "ARCHIVED").contains(response.data().status()) &&
            (unboundOwner==null||unboundOwner.equals(response.data().instructorId())) &&
            referencesCover(response.data().thumbnailUrl(),media);
    }
    public static boolean referencesCover(String url,UUID media) {
        if(url==null) return false;
        if(("/api/v1/media/"+media+"/cover").equals(url)) return true;
        try {
            var uri=java.net.URI.create(url);
            return uri.getRawQuery()!=null&&uri.getRawQuery().contains("X-Amz-Signature=")&&
                uri.getPath()!=null&&uri.getPath().startsWith("/lms-images/images/"+media+"/");
        } catch(IllegalArgumentException ex) {return false;}
    }

    public boolean canEdit(UUID course, UUID user) {
        if (user == null) return false;
        var response = client
            .get()
            .uri("/api/v1/internal/courses/{id}/authors/{user}", course, user)
            .retrieve()
            .body(Permission.class);
        return (
            response != null &&
            response.success() &&
            Boolean.TRUE.equals(response.data())
        );
    }

    public boolean owner(UUID course, UUID user) {
        var response = client
            .get()
            .uri("/api/v1/internal/courses/{id}", course)
            .retrieve()
            .body(Envelope.class);
        return (
            response != null &&
            response.success() &&
            response.data() != null &&
            user.equals(response.data().instructorId())
        );
    }

    public boolean preview(UUID course, UUID media) {
        var response = client
            .get()
            .uri("/api/v1/internal/courses/{id}", course)
            .retrieve()
            .body(Envelope.class);
        if (
            response == null ||
            !response.success() ||
            response.data() == null ||
            !"PUBLISHED".equals(response.data().status()) ||
            response.data().sections() == null
        ) return false;
        String expected = "/api/v1/media/" + media + "/manifest.m3u8";
        return response
            .data()
            .sections()
            .stream()
            .filter(s -> s.lessons() != null)
            .flatMap(s -> s.lessons().stream())
            .anyMatch(l -> l.previewAllowed() && expected.equals(l.videoUrl()));
    }
}
