package com.lms.courseservice.service;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.exception.*;
import com.lms.courseservice.repository.*;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscussionService {

    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final CourseAccessPolicy access;
    private final JdbcTemplate jdbc;

    public record QuestionInput(
        UUID lessonId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content
    ) {}

    public record ReplyInput(@NotBlank @Size(max = 10000) String content) {}

    public record Question(
        UUID id,
        UUID lessonId,
        UUID authorId,
        String authorName,
        String title,
        String content,
        UUID acceptedReplyId,
        long replyCount,
        LocalDateTime createdAt
    ) {}

    public record Reply(
        UUID id,
        UUID authorId,
        String authorName,
        boolean instructor,
        String content,
        LocalDateTime createdAt
    ) {}

    private Course require(UUID courseId, AuthenticatedUser user) {
        var course = courses
            .findById(courseId)
            .orElseThrow(() -> new CourseNotFoundException("Kurs bulunamadı"));
        if (!access.canReadContent(course, user)) throw new UnauthorizedException(
            "Soru-cevap için kursa kayıtlı olmalısınız"
        );
        return course;
    }

    private Question question(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Question(
            r.getObject("id", UUID.class),
            r.getObject("lesson_id", UUID.class),
            r.getObject("author_id", UUID.class),
            r.getString("author_name"),
            r.getString("title"),
            r.getString("content"),
            r.getObject("accepted_reply_id", UUID.class),
            r.getLong("reply_count"),
            r.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private Question get(UUID courseId, UUID questionId) {
        return jdbc
            .query(
                "select q.*, (select count(*) from question_replies r where r.question_id=q.id) as reply_count from course_questions q where q.id=? and q.course_id=?",
                this::question,
                questionId,
                courseId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new CourseNotFoundException("Soru bulunamadı"));
    }

    @Transactional(readOnly = true)
    public Page<Question> list(
        UUID courseId,
        UUID lessonId,
        String query,
        boolean unanswered,
        AuthenticatedUser user,
        Pageable pageable
    ) {
        require(courseId, user);
        String where =
            " where q.course_id=? and (?::uuid is null or q.lesson_id=?) and lower(q.title) like lower(?) and (?=false or not exists(select 1 from question_replies r where r.question_id=q.id))";
        Object[] parameters = { courseId, lessonId, lessonId, "%" + query + "%", unanswered };
        var total = jdbc.queryForObject(
            "select count(*) from course_questions q" + where,
            Long.class,
            parameters
        );
        var args = new ArrayList<Object>(Arrays.asList(parameters));
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        var rows = jdbc.query(
            "select q.*, (select count(*) from question_replies r where r.question_id=q.id) as reply_count from course_questions q" +
                where +
                " order by q.created_at desc limit ? offset ?",
            this::question,
            args.toArray()
        );
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    @Transactional
    public Question ask(UUID courseId, QuestionInput input, AuthenticatedUser user) {
        require(courseId, user);
        if (input.lessonId() != null) {
            var lesson = lessons
                .findById(input.lessonId())
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));
            if (!lesson.getSection().getCourse().getId().equals(courseId)) throw new IllegalArgumentException(
                "Ders bu kursa ait değil"
            );
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into course_questions(id,course_id,lesson_id,author_id,author_name,title,content) values(?,?,?,?,?,?,?)",
            id,
            courseId,
            input.lessonId(),
            user.id(),
            user.displayName(),
            input.title().trim(),
            input.content().trim()
        );
        return get(courseId, id);
    }

    @Transactional(readOnly = true)
    public List<Reply> replies(UUID courseId, UUID questionId, AuthenticatedUser user) {
        require(courseId, user);
        get(courseId, questionId);
        return jdbc.query(
            "select * from question_replies where question_id=? order by created_at limit 500",
            (r, n) ->
                new Reply(
                    r.getObject("id", UUID.class),
                    r.getObject("author_id", UUID.class),
                    r.getString("author_name"),
                    r.getBoolean("instructor"),
                    r.getString("content"),
                    r.getTimestamp("created_at").toLocalDateTime()
                ),
            questionId
        );
    }

    @Transactional
    public Reply reply(UUID courseId, UUID questionId, ReplyInput input, AuthenticatedUser user) {
        var course = require(courseId, user);
        get(courseId, questionId);
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into question_replies(id,question_id,author_id,author_name,instructor,content) values(?,?,?,?,?,?)",
            id,
            questionId,
            user.id(),
            user.displayName(),
            course.getInstructorId().equals(user.id()),
            input.content().trim()
        );
        return new Reply(
            id,
            user.id(),
            user.displayName(),
            course.getInstructorId().equals(user.id()),
            input.content().trim(),
            LocalDateTime.now()
        );
    }

    @Transactional
    public void accept(UUID courseId, UUID questionId, UUID replyId, AuthenticatedUser user) {
        var course = require(courseId, user);
        var question = get(courseId, questionId);
        if (
            !question.authorId().equals(user.id()) &&
            !course.getInstructorId().equals(user.id()) &&
            !user.isAdmin()
        ) throw new UnauthorizedException("Yanıtı yalnızca soru sahibi veya eğitmen kabul edebilir");
        if (
            jdbc.queryForObject(
                "select count(*) from question_replies where id=? and question_id=?",
                Long.class,
                replyId,
                questionId
            ) == 0
        ) throw new CourseNotFoundException("Yanıt bulunamadı");
        jdbc.update("update course_questions set accepted_reply_id=? where id=?", replyId, questionId);
    }

    @Transactional
    public void delete(UUID courseId, UUID questionId, AuthenticatedUser user) {
        var course = require(courseId, user);
        var question = get(courseId, questionId);
        if (
            !question.authorId().equals(user.id()) &&
            !course.getInstructorId().equals(user.id()) &&
            !user.isAdmin()
        ) throw new UnauthorizedException("Bu soruyu silme yetkiniz yok");
        jdbc.update("delete from course_questions where id=?", questionId);
    }
}
