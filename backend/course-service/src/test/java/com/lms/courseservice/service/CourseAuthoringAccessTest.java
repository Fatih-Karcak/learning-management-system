package com.lms.courseservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.courseservice.client.InstructorDirectory;
import com.lms.courseservice.domain.entity.Course;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourseAuthoringAccessTest {

    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final InstructorDirectory directory = mock(InstructorDirectory.class);
    final CourseAuthoringAccess access = new CourseAuthoringAccess(
        jdbc,
        directory
    );
    final Course course = Course.builder()
        .id(UUID.randomUUID())
        .instructorId(UUID.randomUUID())
        .build();
    final UUID editor = UUID.randomUUID();

    @Test
    void ownershipDoesNotDependOnMembershipOrDirectoryAvailability() {
        assertTrue(access.canEdit(course, course.getInstructorId()));
        verifyNoInteractions(jdbc, directory);
    }

    @Test
    void anAcceptedMembershipDoesNotOverrideTheCurrentInstructorAccountStatus() {
        when(
            jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(course.getId()),
                eq(editor)
            )
        ).thenReturn(true);
        when(directory.active(editor)).thenReturn(false);
        assertFalse(access.canEdit(course, editor));
    }

    @Test
    void revocationIsObservedOnTheNextPermissionCheck() {
        when(
            jdbc.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(course.getId()),
                eq(editor)
            )
        ).thenReturn(true, false);
        when(directory.active(editor)).thenReturn(true);
        assertTrue(access.canEdit(course, editor));
        assertFalse(access.canEdit(course, editor));
        verify(directory, times(1)).active(editor);
    }
}
