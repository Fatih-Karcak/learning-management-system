package com.lms.courseservice.repository;

import com.lms.courseservice.domain.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {
    List<Section> findByCourseIdOrderByOrderIndexAsc(UUID courseId);
}