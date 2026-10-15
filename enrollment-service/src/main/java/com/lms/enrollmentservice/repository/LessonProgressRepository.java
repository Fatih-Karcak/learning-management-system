package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.LessonProgress;
import com.lms.enrollmentservice.domain.enums.CompletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface LessonProgressRepository extends JpaRepository<LessonProgress,UUID>{
    List<LessonProgress> findByEnrollmentId(UUID EnrollmentId);
    Optional <LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId,UUID lessonId);
    @Query(
            """
 SELECT COUNT(lp) FROM LessonProgress lp
        WHERE lp.enrollment.id = :enrollmentId
        AND lp.status = 'COMPLETED'
"""
    )
    int countCompletedLessons(UUID enrollmentId);

    boolean existsByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);





}
