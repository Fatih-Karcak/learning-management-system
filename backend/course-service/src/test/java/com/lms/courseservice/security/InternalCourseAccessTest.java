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
    void noCourseMetadataIsReadWithoutTheSharedKey() {
        var service = mock(CourseService.class);
        UUID course = UUID.randomUUID();
        for (String configured : new String[] { "", "shared-key" }) {
            var controller = new InternalCourseController(service, configured);
            for (String given : new String[] { null, "", "wrong" })
                assertThrows(ResponseStatusException.class, () -> controller.get(course, given));
        }
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedInternalClientCanReadArchivedCourseMetadata() {
        var service = mock(CourseService.class);
        UUID course = UUID.randomUUID();
        assertTrue(new InternalCourseController(service, "shared-key").get(course, "shared-key").isSuccess());
        verify(service).getInternalCourse(course);
    }
}
