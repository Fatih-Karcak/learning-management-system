package com.lms.notificationservice.kafka;

import com.lms.notificationservice.event.CoursePublishedEvent;
import com.lms.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {
    private final NotificationService notificationService;

    @KafkaListener(topics = "course-published",groupId = "notification-service",containerFactory = "coursePublishedContainerFactory")
    public void handleCoursePublished(CoursePublishedEvent event) {
        log.info("Kurs yayınlama eventi alındı: {}", event.courseId());
        notificationService.sendCoursePublishedEmail(event);
    }

}
