package com.lms.courseservice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.controller.InternalCourseController;
import com.lms.courseservice.service.CourseService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalCourseAccessTest {

    @Test
    void authorshipRequiresTheSharedServiceKey() {
        var authors = mock(
            com.lms.courseservice.service.CourseAuthoringAccess.class
        );
        var controller = new InternalCourseController(
            mock(CourseService.class),
            "shared-key",
            authors
        );
        var course = UUID.randomUUID();
        var user = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () ->
            controller.author(course, user, null)
        );
        verifyNoInteractions(authors);
        when(authors.canEdit(course, user)).thenReturn(true);
        assertEquals(
            true,
            controller.author(course, user, "shared-key").getData()
        );
    }

    @Test
    void noCourseMetadataIsReadWithoutTheSharedKey() {
        var service = mock(CourseService.class);
        UUID course = UUID.randomUUID();
        for (String configured : new String[] { "", "shared-key" }) {
            var controller = new InternalCourseController(
                service,
                configured,
                mock(com.lms.courseservice.service.CourseAuthoringAccess.class)
            );
            for (String given : new String[] { null, "", "wrong" })
                assertThrows(ResponseStatusException.class, () ->
                    controller.get(course, given)
                );
        }
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedInternalClientCanReadArchivedCourseMetadata() {
        var service = mock(CourseService.class);
        UUID course = UUID.randomUUID();
        assertTrue(
            new InternalCourseController(
                service,
                "shared-key",
                mock(com.lms.courseservice.service.CourseAuthoringAccess.class)
            )
                .get(course, "shared-key")
                .isSuccess()
        );
        verify(service).getInternalCourse(course);
    }
}
