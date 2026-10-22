package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.client.UserClient;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.response.*;
import com.lms.enrollmentservice.event.CourseEnrolledEvent;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LearningManagementService {

    private final EnrollmentRepository enrollments;
    private final EnrollmentMapper mapper;
    private final CourseCatalogService catalog;
    private final UserClient users;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;

    public record Grant(
        @NotNull UUID courseId,
        @NotNull UUID studentId,
        @NotBlank @Size(max = 1000) String reason
    ) {}

    public record RosterRow(EnrollmentResponse enrollment, String studentName, String email) {}

    @Transactional
    public EnrollmentResponse enrollFree(UUID courseId, UUID student) {
        var course = catalog.requireCourse(courseId);
        BigDecimal price = course.discountPrice() != null ? course.discountPrice() : course.price();
        if (price == null || price.signum() != 0) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Bu kurs ücretsiz değil"
        );
        return create(course, student, null, null);
    }

    @Transactional
    public EnrollmentResponse grant(Grant request, UUID admin) {
        var user = users.getUserById(request.studentId());
        if (
            user == null ||
            !user.success() ||
            user.data() == null ||
            !request.studentId().equals(user.data().id())
        ) throw new IllegalArgumentException("Öğrenci bulunamadı");
        return create(
            catalog.requireCourse(request.courseId()),
            request.studentId(),
            admin,
            request.reason()
        );
    }

    private EnrollmentResponse create(CourseSummaryResponse course, UUID student, UUID admin, String reason) {
        if (
            !"PUBLISHED".equals(course.status()) || course.totalLessons() == 0
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Yalnızca yayındaki ve ders içeren kursa kayıt yapılabilir"
        );
        if (student.equals(course.instructorId())) throw new IllegalArgumentException(
            "Kendi kursunuza öğrenci olarak kaydolamazsınız"
        );
        UUID proposed = UUID.randomUUID();
        var inserted = jdbc.queryForList(
            "insert into enrollments(id,student_id,course_id,course_title,course_thumbnail_url,instructor_name,price_paid,status,total_lessons,completed_lessons,progress_percentage) " +
                "values(?,?,?,?,?,?,0,'ACTIVE',?,0,0) on conflict(student_id,course_id) do nothing returning id",
            proposed,
            student,
            course.id(),
            course.title(),
            course.thumbnailUrl(),
            course.instructorName(),
            course.totalLessons()
        );
        var enrollment = enrollments
            .findByStudentIdAndCourseId(student, course.id())
            .orElseThrow(() -> new IllegalStateException("Kayıt oluşturulamadı"));
        if (
            enrollment.getStatus() != EnrollmentStatus.ACTIVE &&
            enrollment.getStatus() != EnrollmentStatus.COMPLETED
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Mevcut kayıt aktif değil; yöneticiyle iletişim kurun"
        );
        if (!inserted.isEmpty()) {
            var event = new CourseEnrolledEvent(
                enrollment.getId(),
                student,
                course.id(),
                null,
                course.title()
            );
            outbox.enqueue("course-enrolled", course.id().toString(), event);
            outbox.enqueue("enrollment-created", enrollment.getId().toString(), event);
            if (admin != null) jdbc.update(
                "insert into enrollment_admin_audit(id,actor_id,enrollment_id,action,reason) values(?,?,?,'GRANT',?)",
                UUID.randomUUID(),
                admin,
                enrollment.getId(),
                reason
            );
        }
        return mapper.toResponse(enrollment);
    }

    public Map<String, Object> summary(UUID student) {
        if (student == null) return jdbc.queryForMap(
            "select count(*) as total,count(*) filter(where status='ACTIVE') as active,count(*) filter(where status='COMPLETED') as completed,coalesce(avg(progress_percentage),0) as average_progress from enrollments"
        );
        return jdbc.queryForMap(
            "select count(*) as total,count(*) filter(where status='ACTIVE') as active,count(*) filter(where status='COMPLETED') as completed,coalesce(avg(progress_percentage),0) as average_progress from enrollments where student_id=?",
            student
        );
    }

    @Transactional(readOnly = true)
    public Page<RosterRow> roster(UUID courseId, UUID instructor, boolean admin, Pageable pageable) {
        var course = catalog.requireCourse(courseId);
        if (!admin && !instructor.equals(course.instructorId())) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Kurs öğrencilerine erişim yetkiniz yok"
        );
        return enrollments.findByCourseId(courseId, pageable).map(e -> {
            var user = users.getUserById(e.getStudentId());
            var data = user != null ? user.data() : null;
            return new RosterRow(
                mapper.toResponse(e),
                data == null ? "Öğrenci" : data.firstName() + " " + data.lastName(),
                data == null ? "" : data.email()
            );
        });
    }

    @Transactional
    public void revoke(UUID id, String reason, UUID admin) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException(
            "İptal gerekçesi gerekli"
        );
        var enrollment = enrollments
            .findByIdForUpdate(id)
            .orElseThrow(() -> new EnrollmentNotFoundException("Kayıt bulunamadı"));
        // Financial enrollments must use their existing refund workflow.
        if (enrollment.getPaymentId() != null) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Ödemeli kaydın erişimi ödeme/iade akışından yönetilir"
        );
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        jdbc.update(
            "insert into enrollment_admin_audit(id,actor_id,enrollment_id,action,reason) values(?,?,?,'REVOKE',?)",
            UUID.randomUUID(),
            admin,
            id,
            reason
        );
    }
}
