package com.lms.notificationservice.event;

import java.util.UUID;

// course-service → CourseService.CoursePublishedEvent ile alan uyumlu
public record CoursePublishedEvent(UUID courseId, String title, UUID instructorId) {}