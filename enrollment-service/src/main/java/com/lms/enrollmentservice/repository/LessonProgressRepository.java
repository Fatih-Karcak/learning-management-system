package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.LessonProgress;
import com.lms.enrollmentservice.domain.enums.CompletionStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository
        extends JpaRepository<LessonProgress, UUID> {

    List<LessonProgress> findByEnrollmentId(
            UUID enrollmentId);

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(
            UUID enrollmentId,
            UUID lessonId);

    long countByEnrollmentIdAndStatusAndLessonIdIn(
            UUID enrollmentId,
            CompletionStatus status,
            Collection<UUID> lessonIds);

    boolean existsByEnrollmentIdAndLessonId(
            UUID enrollmentId,
            UUID lessonId);
}