package com.lms.enrollmentservice.repository;
import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment,UUID> {
    Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId,UUID courseId);
    boolean existsByStudentIdAndCourseId(UUID studentId,UUID courseId);
    Page<Enrollment> findByStudentId(UUID studentId,Pageable pageable);
    Page<Enrollment> findByStudentIdAndStatus(UUID studentId,EnrollmentStatus status,Pageable pageable);
    @Query("""
Select COUNT(e) FROM Enrollment e WHERE e.courseId= :courseId AND e.status= 'ACTIVE'
"""
    )
    long countActiveEnrollmentsByCourse(UUID courseId);
    @Query("""
    Select e FROM Enrollment e WHERE e.studentId= :studentId AND e.status = 'COMPLETED' 
    
""")
    Page<Enrollment> findByCompletedByStudent(UUID studentId,Pageable pageable);



}
