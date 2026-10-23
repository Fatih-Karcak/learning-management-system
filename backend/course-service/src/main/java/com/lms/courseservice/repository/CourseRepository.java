package com.lms.courseservice.repository;

import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository
    extends
        JpaRepository<Course, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Course>
{
    long countByStatus(CourseStatus status);
    boolean existsByCategoryId(Long categoryId);

    @Query(
        "select c from Course c where (:status is null or c.status=:status) and " +
            "(lower(c.title) like lower(concat('%',:query,'%')) or lower(c.instructorName) like lower(concat('%',:query,'%')))"
    )
    Page<Course> adminSearch(String query, CourseStatus status, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
        value = "INSERT INTO processed_course_enrollments(enrollment_id, course_id) VALUES (:enrollmentId, :courseId) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int claimEnrollment(UUID enrollmentId, UUID courseId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
        "UPDATE Course c SET c.totalEnrollments = COALESCE(c.totalEnrollments, 0) + 1 WHERE c.id = :courseId"
    )
    int incrementEnrollmentCount(UUID courseId);

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByInstructorIdAndStatus(UUID instructorId, CourseStatus status, Pageable pageable);

    List<Course> findByInstructorId(UUID instructorId);

    Page<Course> findByInstructorId(UUID instructorId, Pageable pageable);

    @Query(
        """
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        AND (
            LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(c.instructorName) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        """
    )
    Page<Course> searchPublishedCourses(@Param("query") String query, Pageable pageable);

    @Query(
        """
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        AND c.category.id = :categoryId
        """
    )
    Page<Course> findByCategoryIdAndPublished(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query(
        """
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        ORDER BY c.totalEnrollments DESC
        """
    )
    Page<Course> findPopularCourses(Pageable pageable);

    @Query(
        """
        SELECT c FROM Course c
        WHERE c.status = 'PUBLISHED'
        ORDER BY c.createdAt DESC
        """
    )
    Page<Course> findNewestCourses(Pageable pageable);
}
