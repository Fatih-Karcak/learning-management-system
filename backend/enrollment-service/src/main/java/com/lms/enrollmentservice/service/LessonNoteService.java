package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonNoteService {

    private final EnrollmentRepository enrollments;
    private final CourseCatalogService courses;
    private final JdbcTemplate jdbc;

    public record Input(
        @NotNull UUID lessonId,
        @NotNull UUID sectionId,
        @Min(0) int positionSeconds,
        @NotBlank @Size(max = 10000) String content
    ) {}

    public record Note(
        UUID id,
        UUID lessonId,
        int positionSeconds,
        String content,
        LocalDateTime updatedAt
    ) {}

    private com.lms.enrollmentservice.domain.entity.Enrollment require(UUID enrollmentId, UUID student) {
        var enrollment = enrollments
            .findById(enrollmentId)
            .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));
        if (
            !enrollment.getStudentId().equals(student) ||
            (enrollment.getStatus() != EnrollmentStatus.ACTIVE &&
                enrollment.getStatus() != EnrollmentStatus.COMPLETED)
        ) throw new EnrollmentNotFoundException("Aktif kayıt bulunamadı");
        return enrollment;
    }

    @Transactional(readOnly = true)
    public List<Note> list(UUID enrollmentId, UUID lessonId, UUID student) {
        require(enrollmentId, student);
        return jdbc.query(
            "select * from lesson_notes where enrollment_id=? and (?::uuid is null or lesson_id=?) order by position_seconds,created_at limit 500",
            (r, n) ->
                new Note(
                    r.getObject("id", UUID.class),
                    r.getObject("lesson_id", UUID.class),
                    r.getInt("position_seconds"),
                    r.getString("content"),
                    r.getTimestamp("updated_at").toLocalDateTime()
                ),
            enrollmentId,
            lessonId,
            lessonId
        );
    }

    @Transactional
    public Note save(UUID enrollmentId, UUID noteId, Input input, UUID student) {
        var enrollment = require(enrollmentId, student);
        var lesson = courses
            .requireCourse(enrollment.getCourseId())
            .findLesson(input.sectionId(), input.lessonId());
        if (lesson == null) throw new IllegalArgumentException("Notun dersi kursa ait değil");
        if (
            input.positionSeconds() > 0 &&
            (lesson.durationSeconds() == null || input.positionSeconds() > lesson.durationSeconds())
        ) throw new IllegalArgumentException("Not konumu ders süresini aşıyor");
        UUID id = noteId == null ? UUID.randomUUID() : noteId;
        if (noteId == null) jdbc.update(
            "insert into lesson_notes(id,enrollment_id,lesson_id,position_seconds,content) values(?,?,?,?,?)",
            id,
            enrollmentId,
            input.lessonId(),
            input.positionSeconds(),
            input.content().trim()
        );
        else if (
            jdbc.update(
                "update lesson_notes set lesson_id=?,position_seconds=?,content=?,updated_at=now() where id=? and enrollment_id=?",
                input.lessonId(),
                input.positionSeconds(),
                input.content().trim(),
                id,
                enrollmentId
            ) != 1
        ) throw new EnrollmentNotFoundException("Not bulunamadı");
        return jdbc.queryForObject(
            "select * from lesson_notes where id=?",
            (r, n) ->
                new Note(
                    id,
                    input.lessonId(),
                    r.getInt("position_seconds"),
                    r.getString("content"),
                    r.getTimestamp("updated_at").toLocalDateTime()
                ),
            id
        );
    }

    @Transactional
    public void delete(UUID enrollmentId, UUID noteId, UUID student) {
        require(enrollmentId, student);
        if (
            jdbc.update("delete from lesson_notes where id=? and enrollment_id=?", noteId, enrollmentId) != 1
        ) throw new EnrollmentNotFoundException("Not bulunamadı");
    }
}
