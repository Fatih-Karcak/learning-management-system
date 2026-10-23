package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LearningToolsService {

    private final JdbcTemplate jdbc;
    private final EnrollmentRepository enrollments;
    private final CourseCatalogService courses;

    public record Task(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate dueDate,
        UUID enrollmentId
    ) {}

    public record Bookmark(
        @NotNull UUID lessonId,
        @NotNull UUID sectionId,
        @NotBlank @Size(max = 200) String title,
        @Min(0) int positionSeconds
    ) {}

    private com.lms.enrollmentservice.domain.entity.Enrollment require(UUID id, UUID user) {
        var e = enrollments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (
            !e.getStudentId().equals(user) ||
            (e.getStatus() != EnrollmentStatus.ACTIVE && e.getStatus() != EnrollmentStatus.COMPLETED)
        ) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return e;
    }

    public Map<String, Object> dashboard(UUID user) {
        return Map.of(
            "daily",
            jdbc.queryForList(
                "select day,watched_millis/60000.0 as minutes from study_daily where student_id=? and day>=current_date-27 order by day",
                user
            ),
            "weekMinutes",
            jdbc.queryForObject(
                "select coalesce(sum(watched_millis),0)/60000.0 from study_daily where student_id=? and day>=date_trunc('week',now())::date",
                Double.class,
                user
            ),
            "tasks",
            tasks(user),
            "bookmarks",
            jdbc.queryForList(
                "select b.*,e.course_title from lesson_bookmarks b join enrollments e on e.id=b.enrollment_id where e.student_id=? and e.status in('ACTIVE','COMPLETED') order by b.created_at desc limit 100",
                user
            )
        );
    }

    public List<Map<String, Object>> tasks(UUID user) {
        return jdbc.queryForList(
            "select * from learning_tasks where student_id=? order by done,due_date limit 200",
            user
        );
    }

    public void createTask(UUID user, Task t) {
        if (t.enrollmentId() != null) require(t.enrollmentId(), user);
        if (
            t.dueDate().isBefore(LocalDate.now().minusDays(1)) ||
            t.dueDate().isAfter(LocalDate.now().plusYears(2))
        ) throw new IllegalArgumentException("Plan tarihi geçersiz");
        jdbc.update(
            "insert into learning_tasks(id,student_id,title,due_date,enrollment_id) values(?,?,?,?,?)",
            UUID.randomUUID(),
            user,
            t.title(),
            t.dueDate(),
            t.enrollmentId()
        );
    }

    public void task(UUID user, UUID id, boolean done) {
        if (
            jdbc.update("update learning_tasks set done=? where id=? and student_id=?", done, id, user) != 1
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public void deleteTask(UUID user, UUID id) {
        jdbc.update("delete from learning_tasks where id=? and student_id=?", id, user);
    }

    public List<Map<String, Object>> bookmarks(UUID enrollment, UUID user) {
        require(enrollment, user);
        return jdbc.queryForList(
            "select * from lesson_bookmarks where enrollment_id=? order by created_at desc limit 200",
            enrollment
        );
    }

    public void bookmark(UUID enrollment, UUID user, Bookmark b) {
        var e = require(enrollment, user);
        var l = courses.requireCourse(e.getCourseId()).findLesson(b.sectionId(), b.lessonId());
        if (
            l == null ||
            b.positionSeconds() > Math.max(0, l.durationSeconds() == null ? 0 : l.durationSeconds())
        ) throw new IllegalArgumentException("Ders veya konum geçersiz");
        jdbc.update(
            "insert into lesson_bookmarks(id,enrollment_id,lesson_id,title,position_seconds) values(?,?,?,?,?)",
            UUID.randomUUID(),
            enrollment,
            b.lessonId(),
            b.title(),
            b.positionSeconds()
        );
    }

    public void deleteBookmark(UUID enrollment, UUID user, UUID id) {
        require(enrollment, user);
        jdbc.update("delete from lesson_bookmarks where id=? and enrollment_id=?", id, enrollment);
    }

    public Map<String, Object> analytics(UUID course, UUID user, boolean admin) {
        var c = courses.requireCourse(course);
        if (!admin && !user.equals(c.instructorId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Map.of(
            "summary",
            jdbc.queryForMap(
                "select count(*) as enrolled,count(*) filter(where status='COMPLETED') as completed,count(*) filter(where status='ACTIVE' and (last_accessed_at is null or last_accessed_at<now()-interval '14 days')) as inactive,coalesce(avg(progress_percentage),0) as average_progress from enrollments where course_id=? and status in('ACTIVE','COMPLETED')",
                course
            ),
            "lessons",
            jdbc.queryForList(
                "select p.lesson_id,count(*) as started,count(*) filter(where p.status='COMPLETED') as completed,coalesce(avg(p.credited_millis),0)/1000 as average_watched_seconds from lesson_progress p join enrollments e on e.id=p.enrollment_id where e.course_id=? and e.status in('ACTIVE','COMPLETED') group by p.lesson_id",
                course
            ),
            "weekly",
            jdbc.queryForList(
                "select date_trunc('week',enrolled_at)::date as week,count(*) as enrollments from enrollments where course_id=? and enrolled_at>=now()-interval '12 weeks' group by 1 order by 1",
                course
            )
        );
    }

    public Map<String, Object> export(UUID user) {
        return Map.of(
            "enrollments",
            jdbc.queryForList("select * from enrollments where student_id=?", user),
            "progress",
            jdbc.queryForList(
                "select p.* from lesson_progress p join enrollments e on e.id=p.enrollment_id where e.student_id=?",
                user
            ),
            "notes",
            jdbc.queryForList(
                "select n.* from lesson_notes n join enrollments e on e.id=n.enrollment_id where e.student_id=?",
                user
            ),
            "learning",
            dashboard(user)
        );
    }
}
