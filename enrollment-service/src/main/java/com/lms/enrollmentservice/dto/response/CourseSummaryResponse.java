package com.lms.enrollmentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseSummaryResponse(
        UUID id,
        String title,
        String thumbnailUrl,
        String instructorName,
        BigDecimal price,
        String status,
        List<Section> sections
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            UUID id,
            List<Lesson> lessons
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lesson(
            UUID id,
            Integer durationSeconds,
            String type
    ) {}

    public Set<UUID> lessonIds() {
        if (sections == null) {
            return Set.of();
        }

        return sections.stream()
                .filter(section -> section != null
                        && section.lessons() != null)
                .flatMap(section -> section.lessons().stream())
                .filter(lesson -> lesson != null
                        && lesson.id() != null)
                .map(Lesson::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public int totalLessons() {
        return lessonIds().size();
    }

    public Lesson findLesson(
            UUID sectionId,
            UUID lessonId) {

        if (sections == null) {
            return null;
        }

        return sections.stream()
                .filter(section ->
                        section != null
                                && sectionId.equals(section.id())
                                && section.lessons() != null)
                .flatMap(section -> section.lessons().stream())
                .filter(lesson ->
                        lesson != null
                                && lessonId.equals(lesson.id()))
                .findFirst()
                .orElse(null);
    }
}