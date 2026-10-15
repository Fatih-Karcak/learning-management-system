package com.lms.notificationservice.event;

import java.util.UUID;

// enrollment-service → CourseEnrolledEvent ile alan uyumlu (studentEmail bilerek null gelir)
public record EnrollmentCreatedEvent(
        UUID enrollmentId, UUID studentId, UUID courseId, String studentEmail, String courseTitle) {}