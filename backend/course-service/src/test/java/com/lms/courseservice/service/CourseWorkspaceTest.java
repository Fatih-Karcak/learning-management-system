package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.client.ContentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.domain.enums.*;
import com.lms.courseservice.dto.response.*;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.*;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class CourseWorkspaceTest {

    final CourseRepository courses = mock(CourseRepository.class);
    final CategoryRepository categories = mock(CategoryRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final CourseMapper mapper = Mappers.getMapper(CourseMapper.class);
    final UUID owner = UUID.randomUUID(),
        id = UUID.randomUUID();
    final Course course = Course.builder()
        .id(id)
        .instructorId(owner)
        .title("A useful course")
        .description("Details")
        .shortDescription("Summary")
        .price(BigDecimal.ZERO)
        .language("Türkçe")
        .level(CourseLevel.BEGINNER)
        .status(CourseStatus.DRAFT)
        .build();
    final CourseWorkspaceService service = new CourseWorkspaceService(
        courses,
        categories,
        mock(PlatformEvents.class),
        mapper,
        mock(CourseAccessPolicy.class),
        jdbc,
        JsonMapper.builder().build(),
        mock(ContentClient.class),
        Validation.buildDefaultValidatorFactory().getValidator()
    );
    final AuthenticatedUser user = new AuthenticatedUser(owner, "Instructor", "INSTRUCTOR");

    CourseWorkspaceTest() {
        var category = new Category();
        category.setId(1L);
        category.setName("Category");
        course.setCategory(category);
        when(courses.findById(id)).thenReturn(Optional.of(course));
        when(categories.existsById(1L)).thenReturn(true);
    }

    @Test
    void anotherInstructorCannotReadDraftOrWriteAnything() {
        var error = assertThrows(ResponseStatusException.class, () ->
            service.get(id, new AuthenticatedUser(UUID.randomUUID(), "Other", "INSTRUCTOR"))
        );
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(jdbc);
    }

    @Test
    void publishedLessonCannotBeRemovedOrReplacedWithAnotherKind() {
        course.setPublishedAt(LocalDateTime.now());
        course.setStatus(CourseStatus.PUBLISHED);
        var section = Section.builder()
            .id(UUID.randomUUID())
            .course(course)
            .lessons(new ArrayList<>())
            .title("Section")
            .build();
        var lesson = Lesson.builder()
            .id(UUID.randomUUID())
            .section(section)
            .title("Lesson")
            .type(LessonType.ARTICLE)
            .build();
        section.getLessons().add(lesson);
        course.getSections().add(section);
        var response = mapper.toResponse(course);
        response.setSections(List.of());
        var doc = new CourseWorkspaceService.Document(response, 1L, Map.of(), Map.of());
        var error = assertThrows(ResponseStatusException.class, () ->
            service.save(id, new CourseWorkspaceService.Save(0, doc), user)
        );
        assertEquals(409, error.getStatusCode().value());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void oldMutationApisRejectPublishedAndPendingCourses() {
        for (var status : List.of(
            CourseStatus.PUBLISHED,
            CourseStatus.PENDING_REVIEW,
            CourseStatus.ARCHIVED
        )) {
            course.setStatus(status);
            assertThrows(ResponseStatusException.class, () ->
                CourseWorkspaceService.requireDirectEditable(course)
            );
        }
        course.setStatus(CourseStatus.DRAFT);
        assertDoesNotThrow(() -> CourseWorkspaceService.requireDirectEditable(course));
    }
}
