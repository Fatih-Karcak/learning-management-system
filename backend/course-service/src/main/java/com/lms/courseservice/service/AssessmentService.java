package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.Lesson;
import com.lms.courseservice.domain.enums.LessonType;
import com.lms.courseservice.exception.*;
import com.lms.courseservice.repository.LessonRepository;
import com.lms.courseservice.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final LessonRepository lessons;
    private final CourseAccessPolicy access;
    private final EnrollmentClient enrollments;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlatformEvents events;
    private final com.lms.courseservice.client.ContentClient contentClient;

    public record Question(
        UUID id,
        @NotBlank @Size(max = 2000) String prompt,
        @NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 1000) String> options,
        @Min(0) int correctOption,
        @Size(max = 4000) String explanation
    ) {}

    public record Quiz(
        @Min(1) @Max(100) int passingScore,
        @NotNull @Size(min = 1, max = 100) List<@Valid Question> questions,
        @Min(1) @Max(100) Integer maxAttempts,
        @Min(30) @Max(10800) Integer timeLimitSeconds,
        @Min(1) @Max(100) Integer questionCount,
        Boolean randomize
    ) {
        public Quiz {
            randomize = Boolean.TRUE.equals(randomize);
        }

        public Quiz(int passingScore, List<Question> questions) {
            this(passingScore, questions, null, null, null, false);
        }
    }

    public record PublicQuestion(UUID id, String prompt, List<String> options) {}

    public record PublicQuiz(
        int passingScore,
        List<PublicQuestion> questions,
        Integer maxAttempts,
        Integer timeLimitSeconds,
        Integer questionCount,
        boolean randomize
    ) {}

    public record QuizSession(UUID id, java.time.Instant expiresAt, PublicQuiz quiz) {}

    public record StartInput(@NotNull UUID enrollmentId) {}

    public record QuestionFeedback(UUID questionId, boolean correct, String explanation) {}

    public record Answer(@NotNull UUID questionId, @Min(0) int option) {}

    public record AttemptInput(
        @NotNull UUID enrollmentId,
        @NotNull @Size(min = 1, max = 100) List<@Valid Answer> answers,
        UUID sessionId
    ) {
        public AttemptInput(UUID enrollmentId, List<Answer> answers) {
            this(enrollmentId, answers, null);
        }
    }

    public record Attempt(
        UUID id,
        double score,
        boolean passed,
        int correct,
        int total,
        LocalDateTime createdAt,
        List<QuestionFeedback> feedback
    ) {}

    public record SubmissionInput(
        @NotNull UUID enrollmentId,
        @NotBlank @Size(max = 20000) String content,
        @Size(max = 2000) String attachmentUrl
    ) {}

    public enum GradeStatus {
        ACCEPTED,
        CHANGES_REQUESTED,
    }

    public record Grade(
        @NotNull GradeStatus status,
        @Min(0) @Max(100) Integer score,
        @Size(max = 10000) String feedback
    ) {}

    public record Submission(
        UUID id,
        UUID studentId,
        String studentName,
        UUID enrollmentId,
        String content,
        String attachmentUrl,
        String status,
        Integer score,
        String feedback,
        LocalDateTime submittedAt,
        LocalDateTime gradedAt
    ) {}

    private Lesson require(
        UUID course,
        UUID lessonId,
        LessonType type,
        AuthenticatedUser user,
        boolean owner
    ) {
        var lesson = lessons
            .findById(lessonId)
            .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));
        var actual = lesson.getSection().getCourse();
        if (!actual.getId().equals(course) || lesson.getType() != type) throw new IllegalArgumentException(
            "Ders kursa veya istenen türe ait değil"
        );
        if (owner) {
            if (
                !actual.getInstructorId().equals(user.id()) && !user.isAdmin()
            ) throw new UnauthorizedException("Bu dersin eğitmeni değilsiniz");
        } else access.requireEnrolled(course, user.id());
        return lesson;
    }

    private Quiz definition(UUID lesson) {
        var values = jdbc.queryForList(
            "select definition from lesson_quizzes where lesson_id=?",
            String.class,
            lesson
        );
        if (values.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Test henüz hazırlanmadı"
        );
        return json.readValue(values.getFirst(), Quiz.class);
    }

    @Transactional(readOnly = true)
    public Quiz manage(UUID course, UUID lesson, AuthenticatedUser user) {
        require(course, lesson, LessonType.QUIZ, user, true);
        return definition(lesson);
    }

    @Transactional
    public Quiz save(UUID course, UUID lesson, Quiz request, AuthenticatedUser user) {
        CourseWorkspaceService.requireDirectEditable(
            require(course, lesson, LessonType.QUIZ, user, true).getSection().getCourse()
        );
        Set<UUID> ids = new HashSet<>();
        List<Question> normalized = request
            .questions()
            .stream()
            .map(q -> {
                if (q.correctOption() >= q.options().size()) throw new IllegalArgumentException(
                    "Doğru yanıt seçenekler arasında değil"
                );
                UUID id = q.id() == null ? UUID.randomUUID() : q.id();
                if (!ids.add(id)) throw new IllegalArgumentException("Yinelenen soru kimliği");
                return new Question(id, q.prompt().trim(), q.options(), q.correctOption(), q.explanation());
            })
            .toList();
        Quiz quiz = new Quiz(
            request.passingScore(),
            normalized,
            request.maxAttempts(),
            request.timeLimitSeconds(),
            request.questionCount(),
            request.randomize()
        );
        if (
            quiz.questionCount() != null && quiz.questionCount() > normalized.size()
        ) throw new IllegalArgumentException("Seçilen soru sayısı bankayı aşıyor");
        jdbc.update(
            "insert into lesson_quizzes(lesson_id,definition) values(?,?) on conflict(lesson_id) do update set definition=excluded.definition,updated_at=now()",
            lesson,
            json.writeValueAsString(quiz)
        );
        return quiz;
    }

    @Transactional(readOnly = true)
    public PublicQuiz view(UUID course, UUID lesson, AuthenticatedUser user) {
        require(course, lesson, LessonType.QUIZ, user, false);
        var quiz = definition(lesson);
        if (
            quiz.maxAttempts() != null ||
            quiz.timeLimitSeconds() != null ||
            quiz.questionCount() != null ||
            quiz.randomize()
        ) return new PublicQuiz(
            quiz.passingScore(),
            List.of(),
            quiz.maxAttempts(),
            quiz.timeLimitSeconds(),
            quiz.questionCount(),
            quiz.randomize()
        );
        return publicQuiz(quiz);
    }

    private PublicQuiz publicQuiz(Quiz quiz) {
        return new PublicQuiz(
            quiz.passingScore(),
            quiz
                .questions()
                .stream()
                .map(q -> new PublicQuestion(q.id(), q.prompt(), q.options()))
                .toList(),
            quiz.maxAttempts(),
            quiz.timeLimitSeconds(),
            quiz.questionCount(),
            quiz.randomize()
        );
    }

    @Transactional
    public QuizSession start(UUID course, UUID lesson, StartInput input, AuthenticatedUser user) {
        require(course, lesson, LessonType.QUIZ, user, false);
        verifyEnrollment(input.enrollmentId(), course, user.id());
        jdbc.queryForList("select id from lessons where id=? for update", lesson);
        var active = jdbc.queryForList(
            "select * from quiz_sessions where lesson_id=? and student_id=? and enrollment_id=? and not submitted and expires_at>now() order by created_at desc limit 1",
            lesson,
            user.id(),
            input.enrollmentId()
        );
        if (!active.isEmpty()) {
            var a = active.getFirst();
            return new QuizSession(
                (UUID) a.get("id"),
                ((java.sql.Timestamp) a.get("expires_at"))
                    .toLocalDateTime()
                    .toInstant(java.time.ZoneOffset.UTC),
                publicQuiz(json.readValue((String) a.get("definition"), Quiz.class))
            );
        }
        var definition = definition(lesson);
        if (definition.maxAttempts() != null) {
            int used = jdbc.queryForObject(
                "select (select count(*) from quiz_sessions where lesson_id=? and student_id=?)+(select count(*) from quiz_attempts where lesson_id=? and student_id=? and session_id is null)",
                Integer.class,
                lesson,
                user.id(),
                lesson,
                user.id()
            );
            if (used >= definition.maxAttempts()) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Deneme hakkınız doldu"
            );
        }
        var selected = new ArrayList<>(definition.questions());
        if (definition.randomize()) Collections.shuffle(selected, new java.security.SecureRandom());
        int count =
            definition.questionCount() == null
                ? selected.size()
                : Math.min(definition.questionCount(), selected.size());
        var quiz = new Quiz(
            definition.passingScore(),
            List.copyOf(selected.subList(0, count)),
            definition.maxAttempts(),
            definition.timeLimitSeconds(),
            count,
            definition.randomize()
        );
        UUID id = UUID.randomUUID();
        var expiry = LocalDateTime.now(java.time.Clock.systemUTC()).plusSeconds(
            quiz.timeLimitSeconds() == null ? 86400 : quiz.timeLimitSeconds()
        );
        jdbc.update(
            "insert into quiz_sessions(id,lesson_id,student_id,enrollment_id,definition,expires_at) values(?,?,?,?,?,?)",
            id,
            lesson,
            user.id(),
            input.enrollmentId(),
            json.writeValueAsString(quiz),
            expiry
        );
        return new QuizSession(id, expiry.toInstant(java.time.ZoneOffset.UTC), publicQuiz(quiz));
    }

    @Transactional
    public Attempt attempt(UUID course, UUID lessonId, AttemptInput input, AuthenticatedUser user) {
        var lesson = require(course, lessonId, LessonType.QUIZ, user, false);
        verifyEnrollment(input.enrollmentId(), course, user.id());
        Quiz quiz;
        if (input.sessionId() != null) {
            var rows = jdbc.queryForList(
                "select * from quiz_sessions where id=? and lesson_id=? and student_id=? and enrollment_id=? for update",
                input.sessionId(),
                lessonId,
                user.id(),
                input.enrollmentId()
            );
            if (rows.isEmpty()) throw new UnauthorizedException("Test oturumu size ait değil");
            var row = rows.getFirst();
            if (Boolean.TRUE.equals(row.get("submitted"))) throw new IllegalStateException(
                "Test oturumu zaten gönderildi"
            );
            if (
                ((java.sql.Timestamp) row.get("expires_at"))
                    .toLocalDateTime()
                    .isBefore(LocalDateTime.now(java.time.Clock.systemUTC()))
            ) throw new IllegalStateException("Test süresi doldu");
            quiz = json.readValue((String) row.get("definition"), Quiz.class);
        } else {
            quiz = definition(lessonId);
            if (
                quiz.maxAttempts() != null ||
                quiz.timeLimitSeconds() != null ||
                quiz.questionCount() != null ||
                quiz.randomize()
            ) throw new IllegalArgumentException("Önce testi başlatın");
        }
        if (input.answers().size() != quiz.questions().size()) throw new IllegalArgumentException(
            "Tüm soruları yanıtlayın"
        );
        Map<UUID, Integer> answers = new HashMap<>();
        for (Answer answer : input.answers())
            if (answers.put(answer.questionId(), answer.option()) != null) throw new IllegalArgumentException(
                "Yinelenen yanıt"
            );
        var feedback = new ArrayList<QuestionFeedback>();
        int correct = 0;
        for (var question : quiz.questions()) {
            Integer answer = answers.get(question.id());
            if (
                answer == null || answer < 0 || answer >= question.options().size()
            ) throw new IllegalArgumentException("Yanıtlar test sorularıyla uyuşmuyor");
            boolean ok = answer == question.correctOption();
            if (ok) correct++;
            feedback.add(new QuestionFeedback(question.id(), ok, question.explanation()));
        }
        double score = Math.round((correct * 1000.0) / quiz.questions().size()) / 10.0;
        boolean passed = score >= quiz.passingScore();
        if (passed) complete(input.enrollmentId(), lesson, user.id());
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into quiz_attempts(id,lesson_id,student_id,enrollment_id,answers,score,passed) values(?,?,?,?,?,?,?)",
            id,
            lessonId,
            user.id(),
            input.enrollmentId(),
            json.writeValueAsString(input.answers()),
            score,
            passed
        );
        jdbc.update(
            "update quiz_attempts set session_id=?,results=? where id=?",
            input.sessionId(),
            json.writeValueAsString(feedback),
            id
        );
        if (input.sessionId() != null) jdbc.update(
            "update quiz_sessions set submitted=true where id=?",
            input.sessionId()
        );
        return new Attempt(
            id,
            score,
            passed,
            correct,
            quiz.questions().size(),
            LocalDateTime.now(java.time.Clock.systemUTC()),
            feedback
        );
    }

    private void complete(UUID enrollment, Lesson lesson, UUID student) {
        try {
            var result = enrollments.complete(
                enrollment,
                lesson.getId(),
                lesson.getSection().getId(),
                student
            );
            if (result == null || !result.success()) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Ders tamamlanması kaydedilemedi"
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "İlerleme servisine ulaşılamadı; tekrar deneyin",
                ex
            );
        }
    }

    private void verifyEnrollment(UUID enrollment, UUID course, UUID student) {
        try {
            var result = enrollments.verify(enrollment, course, student);
            if (result == null || !result.success()) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Kayıt doğrulanamadı"
            );
            if (!Boolean.TRUE.equals(result.data())) throw new UnauthorizedException(
                "Kayıt bu öğrenciye ve kursa ait değil"
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Kayıt servisine ulaşılamadı",
                ex
            );
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> attempts(UUID course, UUID lesson, AuthenticatedUser user) {
        require(course, lesson, LessonType.QUIZ, user, false);
        return jdbc.queryForList(
            "select id,score,passed,created_at from quiz_attempts where lesson_id=? and student_id=? order by created_at desc limit 20",
            lesson,
            user.id()
        );
    }

    private Submission submission(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        var graded = r.getTimestamp("graded_at");
        return new Submission(
            r.getObject("id", UUID.class),
            r.getObject("student_id", UUID.class),
            r.getString("student_name"),
            r.getObject("enrollment_id", UUID.class),
            r.getString("content"),
            r.getString("attachment_url"),
            r.getString("status"),
            r.getObject("score", Integer.class),
            r.getString("feedback"),
            r.getTimestamp("submitted_at").toLocalDateTime(),
            graded == null ? null : graded.toLocalDateTime()
        );
    }

    @Transactional(readOnly = true)
    public Submission mine(UUID course, UUID lesson, AuthenticatedUser user) {
        require(course, lesson, LessonType.ASSIGNMENT, user, false);
        return jdbc
            .query(
                "select * from assignment_submissions where lesson_id=? and student_id=?",
                this::submission,
                lesson,
                user.id()
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    @Transactional
    public Submission submit(UUID course, UUID lesson, SubmissionInput input, AuthenticatedUser user) {
        require(course, lesson, LessonType.ASSIGNMENT, user, false);
        verifyEnrollment(input.enrollmentId(), course, user.id());
        if (
            input.attachmentUrl() != null &&
            input.attachmentUrl().matches("^/api/v1/media/resources/[0-9a-fA-F-]{36}$")
        ) {
            var resource = contentClient.resource(
                UUID.fromString(input.attachmentUrl().substring(input.attachmentUrl().lastIndexOf('/') + 1))
            );
            if (
                resource == null ||
                !resource.success() ||
                resource.data() == null ||
                !user.id().equals(resource.data().ownerId()) ||
                !course.equals(resource.data().courseId()) ||
                !"ASSIGNMENT".equals(resource.data().purpose())
            ) throw new UnauthorizedException("Ödev dosyası size ve kursa ait değil");
        } else if (input.attachmentUrl() != null && !input.attachmentUrl().isBlank()) {
            var uri = java.net.URI.create(input.attachmentUrl());
            if (
                uri.getHost() == null ||
                !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            ) throw new IllegalArgumentException("Ek bağlantısı http veya https olmalı");
        }
        jdbc.update(
            "insert into assignment_revisions(id,submission_id,content,attachment_url,status,score,feedback) select gen_random_uuid(),id,content,attachment_url,status,score,feedback from assignment_submissions where lesson_id=? and student_id=?",
            lesson,
            user.id()
        );
        int changed = jdbc.update(
            "insert into assignment_submissions(id,lesson_id,student_id,student_name,enrollment_id,content,attachment_url) values(?,?,?,?,?,?,?) " +
                "on conflict(lesson_id,student_id) do update set content=excluded.content,attachment_url=excluded.attachment_url,status='SUBMITTED',score=null,feedback=null,submitted_at=now(),graded_at=null where assignment_submissions.status<>'ACCEPTED'",
            UUID.randomUUID(),
            lesson,
            user.id(),
            user.displayName(),
            input.enrollmentId(),
            input.content().trim(),
            input.attachmentUrl()
        );
        if (changed == 0) throw new IllegalStateException("Onaylanmış ödev tekrar gönderilemez");
        return mine(course, lesson, user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> assignmentHistory(UUID course, UUID lesson, AuthenticatedUser user) {
        var actual = lessons
            .findById(lesson)
            .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı"));
        if (!actual.getSection().getCourse().getId().equals(course)) throw new IllegalArgumentException(
            "Ders kursa ait değil"
        );
        boolean owner = actual.getSection().getCourse().getInstructorId().equals(user.id()) || user.isAdmin();
        if (!owner) access.requireEnrolled(course, user.id());
        return owner
            ? jdbc.queryForList(
                  "select r.*,s.student_name from assignment_revisions r join assignment_submissions s on s.id=r.submission_id where s.lesson_id=? order by r.created_at desc limit 200",
                  lesson
              )
            : jdbc.queryForList(
                  "select r.* from assignment_revisions r join assignment_submissions s on s.id=r.submission_id where s.lesson_id=? and s.student_id=? order by r.created_at desc limit 100",
                  lesson,
                  user.id()
              );
    }

    @Transactional(readOnly = true)
    public Page<Submission> submissions(UUID course, UUID lesson, AuthenticatedUser user, Pageable pageable) {
        require(course, lesson, LessonType.ASSIGNMENT, user, true);
        var count = jdbc.queryForObject(
            "select count(*) from assignment_submissions where lesson_id=?",
            Long.class,
            lesson
        );
        return new PageImpl<>(
            jdbc.query(
                "select * from assignment_submissions where lesson_id=? order by submitted_at desc limit ? offset ?",
                this::submission,
                lesson,
                pageable.getPageSize(),
                pageable.getOffset()
            ),
            pageable,
            count == null ? 0 : count
        );
    }

    @Transactional
    public Submission grade(
        UUID course,
        UUID lessonId,
        UUID submissionId,
        Grade grade,
        AuthenticatedUser user
    ) {
        var lesson = require(course, lessonId, LessonType.ASSIGNMENT, user, true);
        var submission = jdbc
            .query(
                "select * from assignment_submissions where id=? and lesson_id=? for update",
                this::submission,
                submissionId,
                lessonId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new CourseNotFoundException("Ödev bulunamadı"));
        if (
            "ACCEPTED".equals(submission.status()) && grade.status() != GradeStatus.ACCEPTED
        ) throw new IllegalStateException("Tamamlanmış ödevin kabulü geri alınamaz");
        if (
            grade.status() == GradeStatus.CHANGES_REQUESTED &&
            (grade.feedback() == null || grade.feedback().isBlank())
        ) throw new IllegalArgumentException("Düzeltme için geri bildirim gerekli");
        if (grade.status() == GradeStatus.ACCEPTED) complete(
            submission.enrollmentId(),
            lesson,
            submission.studentId()
        );
        jdbc.update(
            "update assignment_submissions set status=?,score=?,feedback=?,graded_at=now() where id=?",
            grade.status().name(),
            grade.score(),
            grade.feedback(),
            submissionId
        );
        events.enqueue(
            new com.lms.courseservice.event.PlatformNotice(
                UUID.randomUUID(),
                submission.studentId(),
                course,
                "Ödeviniz değerlendirildi",
                grade.feedback() == null ? grade.status().name() : grade.feedback(),
                "/courses/" + course,
                "ASSIGNMENT_GRADED",
                null
            )
        );
        return jdbc.queryForObject(
            "select * from assignment_submissions where id=?",
            this::submission,
            submissionId
        );
    }
}
