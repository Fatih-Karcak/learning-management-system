package com.lms.courseservice.service;

import com.lms.courseservice.domain.enums.*;
import com.lms.courseservice.event.PlatformNotice;
import com.lms.courseservice.repository.*;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EngagementService {

    private final JdbcTemplate jdbc;
    private final CourseRepository courses;
    private final CourseAccessPolicy access;
    private final PlatformEvents events;

    public record Announcement(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content
    ) {}

    private void owner(UUID course, AuthenticatedUser user) {
        var c = courses.findById(course).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!c.getInstructorId().equals(user.id()) && !user.isAdmin()) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN
        );
    }

    public List<Map<String, Object>> wishlist(UUID user) {
        return jdbc.queryForList(
            "select c.id,c.title,c.short_description,c.thumbnail_url,c.instructor_name,c.price,c.discount_price,c.status from course_wishlist w join courses c on c.id=w.course_id where w.user_id=? and c.status='PUBLISHED' order by w.created_at desc limit 200",
            user
        );
    }

    public void wish(UUID course, UUID user, boolean add) {
        if (add) {
            if (!courses.existsById(course)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            jdbc.update(
                "insert into course_wishlist(user_id,course_id) values(?,?) on conflict do nothing",
                user,
                course
            );
        } else jdbc.update("delete from course_wishlist where user_id=? and course_id=?", user, course);
    }

    public List<Map<String, Object>> announcements(UUID course, AuthenticatedUser user) {
        var c = courses.findById(course).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!c.getInstructorId().equals(user.id()) && !user.isAdmin()) access.requireEnrolled(
            course,
            user.id()
        );
        return jdbc.queryForList(
            "select * from course_announcements where course_id=? order by created_at desc limit 100",
            course
        );
    }

    @Transactional
    public void announce(UUID course, AuthenticatedUser user, Announcement a) {
        owner(course, user);
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into course_announcements(id,course_id,title,content) values(?,?,?,?)",
            id,
            course,
            a.title(),
            a.content()
        );
        events.enqueue(
            new PlatformNotice(
                id,
                null,
                course,
                a.title(),
                a.content(),
                "/courses/" + course,
                "ANNOUNCEMENT",
                null
            )
        );
    }

    public Map<String, Object> analytics(UUID course, AuthenticatedUser user) {
        owner(course, user);
        return Map.of(
            "quizResults",
            jdbc.queryForList(
                "select a.lesson_id,count(*) as attempts,round(avg(a.score)::numeric,1) as average_score,count(*) filter(where a.passed) as passed from quiz_attempts a join lessons l on l.id=a.lesson_id join sections s on s.id=l.section_id where s.course_id=? group by a.lesson_id",
                course
            ),
            "questions",
            jdbc.queryForList(
                "select q.lesson_id,count(*) as asked,count(*) filter(where q.accepted_reply_id is null) as unresolved from course_questions q where q.course_id=? group by q.lesson_id",
                course
            ),
            "assignments",
            jdbc.queryForList(
                "select a.lesson_id,count(*) filter(where a.status='SUBMITTED') as pending,count(*) filter(where a.status='CHANGES_REQUESTED') as changes_requested,count(*) filter(where a.status='ACCEPTED') as accepted from assignment_submissions a join lessons l on l.id=a.lesson_id join sections s on s.id=l.section_id where s.course_id=? group by a.lesson_id",
                course
            )
        );
    }

    public List<Map<String, Object>> recommendations(UUID course) {
        return jdbc.queryForList(
            "select c.id,c.title,c.short_description,c.thumbnail_url,c.instructor_name,c.price,c.discount_price from courses c where c.status='PUBLISHED' and c.id<>? and c.category_id=(select category_id from courses where id=?) order by c.average_rating desc,c.total_enrollments desc limit 6",
            course,
            course
        );
    }

    public Map<String, Object> export(UUID user) {
        return Map.of(
            "wishlist",
            wishlist(user),
            "reviews",
            jdbc.queryForList("select * from reviews where student_id=?", user),
            "quizAttempts",
            jdbc.queryForList(
                "select id,lesson_id,score,passed,created_at,answers from quiz_attempts where student_id=?",
                user
            ),
            "assignments",
            jdbc.queryForList("select * from assignment_submissions where student_id=?", user),
            "questions",
            jdbc.queryForList("select * from course_questions where author_id=?", user),
            "replies",
            jdbc.queryForList("select * from question_replies where author_id=?", user)
        );
    }

    @Transactional
    public void moderateReview(UUID review, AuthenticatedUser user, String reason) {
        if (!user.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Gerekçe gerekli");
        var rows = jdbc.queryForList("select course_id from reviews where id=?", UUID.class, review);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("delete from reviews where id=?", review);
        jdbc.update(
            "update courses set average_rating=coalesce((select round(avg(rating),1) from reviews where course_id=?),0),total_reviews=(select count(*) from reviews where course_id=?) where id=?",
            rows.getFirst(),
            rows.getFirst(),
            rows.getFirst()
        );
        jdbc.update(
            "insert into course_review_events(id,course_id,actor_id,decision,reason) values(?,?,?,'REMOVE_REVIEW',?)",
            UUID.randomUUID(),
            rows.getFirst(),
            user.id(),
            reason
        );
    }
}
