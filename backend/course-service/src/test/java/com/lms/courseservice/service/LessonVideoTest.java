package com.lms.courseservice.service;

import com.lms.courseservice.client.ContentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.domain.enums.LessonType;
import com.lms.courseservice.dto.request.CreateLessonRequest;
import com.lms.courseservice.dto.request.UpdateLessonVideoRequest;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonVideoTest {
    final LessonRepository lessons = mock(LessonRepository.class);
    final SectionRepository sections = mock(SectionRepository.class);
    final ContentClient content = mock(ContentClient.class);
    final LessonService service = new LessonService(lessons, sections, Mappers.getMapper(CourseMapper.class),
            mock(CourseAccessPolicy.class), content);
    final UUID owner = UUID.randomUUID();
    final UUID mediaId = UUID.randomUUID();
    final String url = "/api/v1/media/" + mediaId + "/manifest.m3u8";
    final Course course = Course.builder().id(UUID.randomUUID()).instructorId(owner).build();
    final Section section = Section.builder().id(UUID.randomUUID()).course(course).build();
    final Lesson lesson = Lesson.builder().id(UUID.randomUUID()).section(section).type(LessonType.ARTICLE)
            .title("Lesson").content("Original article").build();

    LessonVideoTest() {
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(sections.findById(section.getId())).thenReturn(Optional.of(section));
        when(lessons.save(any())).thenAnswer(call -> {
            Lesson value = call.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        when(content.linkVideo(eq(mediaId), any())).thenReturn(new ContentClient.Result(true, "ok", new ContentClient.Media(url, 12)));
    }

    @Test void addingVideoToExistingTextLessonPreservesArticleAndGrantsCourseAssociation() {
        var response = service.updateVideo(lesson.getId(), new UpdateLessonVideoRequest("http://localhost:8085" + url, 0), owner);
        assertEquals(LessonType.VIDEO, response.getType());
        assertEquals("Original article", response.getContent());
        assertEquals(url, response.getVideoUrl());
        assertEquals(12, response.getDurationSeconds());
        verify(content).linkVideo(mediaId, new ContentClient.LinkRequest(course.getId(), lesson.getId(), owner));
    }

    @Test void newVideoLessonLinksUnassignedLibraryMediaToItsCourse() {
        var request = new CreateLessonRequest();
        request.setSectionId(section.getId()); request.setTitle("Video lesson");
        request.setType(LessonType.VIDEO); request.setVideoUrl(url);
        var response = service.createLesson(request, owner);
        assertEquals(url, response.getVideoUrl());
        verify(content).linkVideo(mediaId, new ContentClient.LinkRequest(course.getId(), response.getId(), owner));
    }

    @Test void otherInstructorCannotChangeVideoOrCallInternalBinding() {
        assertThrows(UnauthorizedException.class,
                () -> service.updateVideo(lesson.getId(), new UpdateLessonVideoRequest(url, 0), UUID.randomUUID()));
        verifyNoInteractions(content);
        verify(lessons, never()).save(any());
    }

    @Test void unavailableContentServiceDoesNotReportSuccessfulAttachment() {
        when(content.linkVideo(any(), any())).thenThrow(new ResourceAccessException("offline"));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateVideo(lesson.getId(), new UpdateLessonVideoRequest(url, 0), owner));
        assertEquals(503, error.getStatusCode().value());
        assertNull(lesson.getVideoUrl());
        assertEquals(LessonType.ARTICLE, lesson.getType());
        verify(lessons, never()).save(any());
    }

    @Test void externalVideoDoesNotRequireTheContentService() {
        var response = service.updateVideo(lesson.getId(), new UpdateLessonVideoRequest("https://example.test/lesson.mp4", 10), owner);
        assertEquals("https://example.test/lesson.mp4", response.getVideoUrl());
        verifyNoInteractions(content);
    }
}
