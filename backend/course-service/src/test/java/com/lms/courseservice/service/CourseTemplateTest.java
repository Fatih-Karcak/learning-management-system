package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.domain.enums.LessonType;
import com.lms.courseservice.dto.response.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CourseTemplateTest {

    @Test
    void newCourseGetsIndependentCurriculumAndQuestionsWithoutForeignProtectedFiles() {
        var source = new CourseResponse();
        source.setId(UUID.randomUUID());
        var section = new SectionResponse();
        section.setId(UUID.randomUUID());
        var video = new LessonResponse();
        video.setId(UUID.randomUUID());
        video.setType(LessonType.VIDEO);
        video.setVideoUrl("/api/v1/media/private/manifest.m3u8");
        video.setDurationSeconds(90);
        video.setPreviewAllowed(true);
        var quiz = new LessonResponse();
        quiz.setId(UUID.randomUUID());
        quiz.setType(LessonType.QUIZ);
        section.setLessons(List.of(video, quiz));
        source.setSections(List.of(section));
        var question = new AssessmentService.Question(
            UUID.randomUUID(),
            "Question",
            List.of("A", "B"),
            0,
            "Feedback"
        );
        var bank = new AssessmentService.Quiz(
            70,
            List.of(question),
            2,
            30,
            1,
            true
        );
        var doc = new CourseWorkspaceService.Document(
            source,
            1L,
            Map.of(quiz.getId(), bank),
            Map.of(
                video.getId(),
                List.of(
                    new CourseWorkspaceService.Material(
                        "Private",
                        "/api/v1/media/resources/" + UUID.randomUUID(),
                        "RESOURCE",
                        null
                    ),
                    new CourseWorkspaceService.Material(
                        "Public",
                        "https://academy.test/resource",
                        "RESOURCE",
                        null
                    )
                )
            )
        );
        var created = new CourseResponse();
        created.setId(UUID.randomUUID());
        var service = new CourseTemplateService(
            null,
            null,
            null,
            JsonMapper.builder().build(),
            null
        );
        var result = service.remap(doc, created, 2L);
        assertEquals(created.getId(), result.course().getId());
        assertEquals(2L, result.categoryId());
        var next = result.course().getSections().getFirst();
        assertNotEquals(section.getId(), next.getId());
        assertNotEquals(video.getId(), next.getLessons().getFirst().getId());
        assertNull(next.getLessons().getFirst().getVideoUrl());
        assertFalse(next.getLessons().getFirst().isPreviewAllowed());
        assertEquals(0, next.getLessons().getFirst().getDurationSeconds());
        var copiedQuiz = result.quizzes().get(next.getLessons().get(1).getId());
        assertNotEquals(question.id(), copiedQuiz.questions().getFirst().id());
        assertEquals(2, copiedQuiz.maxAttempts());
        assertEquals(30, copiedQuiz.timeLimitSeconds());
        assertEquals(
            1,
            result.materials().get(next.getLessons().getFirst().getId()).size()
        );
        assertNotNull(video.getVideoUrl());
        assertTrue(video.isPreviewAllowed());
        assertEquals(
            question.id(),
            doc.quizzes().get(quiz.getId()).questions().getFirst().id()
        );
    }
}
