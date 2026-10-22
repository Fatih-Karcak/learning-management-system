package com.lms.enrollmentservice.event;

import java.util.UUID;

public record CourseEnrolledEvent (
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        String studentEmail,
        String courseTitle
) {}
