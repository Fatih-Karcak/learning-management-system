package com.lms.enrollmentservice.kafka;

import com.lms.enrollmentservice.event.PlatformNotice;
import com.lms.enrollmentservice.service.OutboxService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseNoticeConsumer {

    private final JdbcTemplate jdbc;
    private final OutboxService outbox;

    @KafkaListener(
        topics = "course-notices",
        groupId = "enrollment-service",
        containerFactory = "courseNoticeFactory"
    )
    @Transactional
    public void receive(PlatformNotice event) {
        if (event.courseId() == null || event.recipientId() != null) throw new IllegalArgumentException(
            "Kurs duyurusu geçersiz"
        );
        UUID after = new UUID(0, 0);
        int batch = 0;
        while (true) {
            var students = jdbc.queryForList(
                "select student_id from enrollments where course_id=? and status in('ACTIVE','COMPLETED') and student_id>? order by student_id limit 200",
                UUID.class,
                event.courseId(),
                after
            );
            if (students.isEmpty()) break;
            for (UUID student : students) {
                UUID id = UUID.nameUUIDFromBytes(
                    (event.id() + ":" + student).getBytes(StandardCharsets.UTF_8)
                );
                outbox.enqueue(
                    "platform-notices",
                    student.toString(),
                    new PlatformNotice(
                        id,
                        student,
                        event.courseId(),
                        event.subject(),
                        event.message(),
                        event.path(),
                        event.kind(),
                        null
                    )
                );
            }
            after = students.getLast();
            if (students.size() < 200) break;
        }
    }
}
