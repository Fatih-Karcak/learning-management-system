package com.lms.courseservice.repository;

import com.lms.courseservice.domain.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    long countBySectionCourseId(UUID courseId);
    List<Lesson> findBySectionIdOrderByOrderIndexAsc(UUID sectionId);
}
