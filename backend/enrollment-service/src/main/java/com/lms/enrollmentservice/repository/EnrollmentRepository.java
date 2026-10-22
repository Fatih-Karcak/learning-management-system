package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository
        extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByPaymentId(UUID paymentId);

    Optional<Enrollment> findByStudentIdAndCourseId(
            UUID studentId, UUID courseId);

    Page<Enrollment> findByStudentId(
            UUID studentId, Pageable pageable);

    Page<Enrollment> findByStudentIdAndStatus(
            UUID studentId,
            EnrollmentStatus status,
            Pageable pageable);

    boolean existsByStudentIdAndCourseId(
            UUID studentId,
            UUID courseId);

    boolean existsByStudentIdAndCourseIdAndStatusIn(
            UUID studentId,
            UUID courseId,
            List<EnrollmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e FROM Enrollment e
        WHERE e.id = :id
    """)
    Optional<Enrollment> findByIdForUpdate(
            @Param("id") UUID id);

    @Query("""
        SELECT e FROM Enrollment e
        WHERE e.status = :status
        AND NOT EXISTS (
            SELECT c.id FROM Certificate c
            WHERE c.enrollment = e
            AND c.pdfStorageKey IS NOT NULL
        )
    """)
    List<Enrollment> findMissingCertificates(
            @Param("status") EnrollmentStatus status,
            Pageable pageable);

    @Query("""
        SELECT COUNT(e) FROM Enrollment e
        WHERE e.courseId = :courseId
        AND e.status = 'ACTIVE'
    """)
    long countActiveEnrollmentsByCourse(
            @Param("courseId") UUID courseId);

    @Query("""
        SELECT e FROM Enrollment e
        WHERE e.studentId = :studentId
        AND e.status = 'COMPLETED'
    """)
    Page<Enrollment> findByCompletedByStudent(
            @Param("studentId") UUID studentId,
            Pageable pageable);
}