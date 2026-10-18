package com.lms.courseservice.repository;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT INTO processed_course_enrollments(enrollment_id, course_id) VALUES (:enrollmentId, :courseId) ON CONFLICT DO NOTHING", nativeQuery = true)
    int claimEnrollment(UUID enrollmentId, UUID courseId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Course c SET c.totalEnrollments = COALESCE(c.totalEnrollments, 0) + 1 WHERE c.id = :courseId")
    int incrementEnrollmentCount(UUID courseId);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByInstructorIdAndStatus(
            UUID instructorId, CourseStatus status, Pageable pageable);

    List<Course> findByInstructorId(UUID instructorId);

    @Query("""
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        AND (
            LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.instructorName) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        """)
    Page<Course> searchPublishedCourses(
            @Param("query") String query, Pageable pageable);

    @Query("""
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        AND c.category.id = :categoryId
        """)
    Page<Course> findByCategoryIdAndPublished(
            @Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        ORDER BY c.totalEnrollments DESC
        """)
    Page<Course> findPopularCourses(Pageable pageable);

    @Query("""
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        ORDER BY c.createdAt DESC
        """)
    Page<Course> findNewestCourses(Pageable pageable);
}