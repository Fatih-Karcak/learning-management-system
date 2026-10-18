package com.lms.courseservice.kafka;

import com.lms.courseservice.event.CourseEnrolledEvent;
import com.lms.courseservice.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private final CourseService courseService;

    @KafkaListener(
            topics = "course-enrolled",
            groupId = "course-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleCourseEnrolled(CourseEnrolledEvent event) {
        log.info("Kursa kayıt eventi alındı - CourseId: {}, StudentId: {}",
                event.courseId(), event.studentId());
        courseService.incrementEnrollmentCount(event.courseId(), event.enrollmentId());
    }
}