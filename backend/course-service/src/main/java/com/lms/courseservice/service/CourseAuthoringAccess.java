package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Course;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAuthoringAccess {

    private final JdbcTemplate jdbc;
    private final com.lms.courseservice.client.InstructorDirectory directory;

    public boolean canEdit(UUID course, UUID user) {
        if (user == null) return false;
        var owners = jdbc.queryForList(
            "select instructor_id from courses where id=?",
            UUID.class,
            course
        );
        return (
            !owners.isEmpty() &&
            (owners.getFirst().equals(user) || accepted(course, user))
        );
    }

    public boolean canEdit(Course course, UUID user) {
        if (user == null) return false;
        return (
            course.getInstructorId().equals(user) ||
            accepted(course.getId(), user)
        );
    }

    private boolean accepted(UUID course, UUID user) {
        return (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from course_collaborators where course_id=? and user_id=? and status='ACCEPTED')",
                    Boolean.class,
                    course,
                    user
                )
            ) && directory.active(user)
        );
    }
}
