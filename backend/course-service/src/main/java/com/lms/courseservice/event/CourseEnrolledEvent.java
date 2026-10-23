package com.lms.courseservice.event;

import java.util.UUID;

// Enrollment Service'ten gelen event ile alan uyumlu olmalı
public record CourseEnrolledEvent(
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        String studentEmail,
        String courseTitle
) {}