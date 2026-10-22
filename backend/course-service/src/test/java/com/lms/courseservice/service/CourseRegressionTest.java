package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.*;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.request.*;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.mapper.CourseMapper;
import com.lms.courseservice.repository.*;
import com.lms.courseservice.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseRegressionTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final EnrollmentClient enrollment = mock(EnrollmentClient.class);
    private final CourseAccessPolicy access = new CourseAccessPolicy(enrollment);
    private final CourseService service = new CourseService(courses, mock(CategoryRepository.class),
            Mappers.getMapper(CourseMapper.class), mock(KafkaTemplate.class), access);

    private Course course(CourseStatus status) {
        var course = Course.builder().id(UUID.randomUUID()).instructorId(UUID.randomUUID())
                .price(new BigDecimal("100")).status(status).build();
        var lesson = Lesson.builder().id(UUID.randomUUID()).content("Paid article")
                .videoUrl("private-video").previewAllowed(false).build();
        var section = Section.builder().id(UUID.randomUUID()).lessons(List.of(lesson)).build();
        course.getSections().add(section);
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        return course;
    }

    @Test void defaultCountersAndCollectionsAreInitialized() {
        var course = Course.builder().build();
        assertEquals(0, course.getTotalEnrollments()); assertEquals(0, course.getTotalReviews());
        assertEquals(0.0, course.getAverageRating()); assertNotNull(course.getSections());
    }

    @Test void anonymousViewsCannotReadDraftOrNonPreviewContent() {
        var draft = course(CourseStatus.DRAFT);
        assertThrows(CourseNotFoundException.class, () -> service.getCourseById(draft.getId(), null));
        var published = course(CourseStatus.PUBLISHED);
        var response = service.getCourseById(published.getId(), null);
        var lesson = response.getSections().getFirst().getLessons().getFirst();
        assertNull(lesson.getContent()); assertNull(lesson.getVideoUrl());
        assertNotNull(lesson.getId());
        verifyNoInteractions(enrollment);
    }

    @Test void ownerAndEnrolledStudentsCanReadPaidContentButUnenrolledStudentCannot() {
        var course = course(CourseStatus.PUBLISHED);
        var owner = new AuthenticatedUser(course.getInstructorId(), "Owner", "INSTRUCTOR");
        assertEquals("Paid article", service.getCourseById(course.getId(), owner)
                .getSections().getFirst().getLessons().getFirst().getContent());
        var student = new AuthenticatedUser(UUID.randomUUID(), "Student", "STUDENT");
        when(enrollment.check(course.getId(), student.id())).thenReturn(new EnrollmentClient.Result(true, false));
        assertNull(service.getCourseById(course.getId(), student).getSections().getFirst().getLessons().getFirst().getContent());
        when(enrollment.check(course.getId(), student.id())).thenReturn(new EnrollmentClient.Result(true, true));
        assertEquals("Paid article", service.getCourseById(course.getId(), student)
                .getSections().getFirst().getLessons().getFirst().getContent());
    }

    @Test void emptySectionDoesNotMakeCoursePublishable() {
        var course = course(CourseStatus.DRAFT);
        course.getSections().getFirst().setLessons(List.of());
        assertThrows(IllegalStateException.class, () -> service.publishCourse(course.getId(), course.getInstructorId()));
        verify(courses, never()).save(any());
    }

    @Test void negativeAndExcessiveDiscountsAreRejected() {
        var course = course(CourseStatus.DRAFT);
        for (BigDecimal discount : List.of(new BigDecimal("-1"), new BigDecimal("101"))) {
            var request = new UpdateCourseRequest(); request.setDiscountPrice(discount);
            assertThrows(IllegalArgumentException.class, () -> service.updateCourse(course.getId(), request, course.getInstructorId()));
        }
        verify(courses, never()).save(any());
    }

    @Test void duplicateEnrollmentEventsOnlyIncrementOnce() {
        UUID courseId = UUID.randomUUID(); UUID enrollmentId = UUID.randomUUID();
        when(courses.claimEnrollment(enrollmentId, courseId)).thenReturn(1, 0);
        when(courses.incrementEnrollmentCount(courseId)).thenReturn(1);
        service.incrementEnrollmentCount(courseId, enrollmentId);
        service.incrementEnrollmentCount(courseId, enrollmentId);
        verify(courses, times(1)).incrementEnrollmentCount(courseId);
    }

    @Test void reviewsRequireEnrollment() {
        UUID courseId = UUID.randomUUID(); UUID studentId = UUID.randomUUID();
        when(enrollment.check(courseId, studentId)).thenReturn(new EnrollmentClient.Result(true, false));
        assertThrows(UnauthorizedException.class, () -> access.requireEnrolled(courseId, studentId));
    }

    @Test void ownCourseListIncludesDraftsAndUsesAuthenticatedInstructorScope() {
        UUID owner = UUID.randomUUID();
        var draft = course(CourseStatus.DRAFT);
        draft.setInstructorId(owner);
        var published = course(CourseStatus.PUBLISHED);
        published.setInstructorId(owner);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(courses.findByInstructorId(owner, pageable)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(draft, published), pageable, 2));

        var result = service.getOwnCourses(owner, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(CourseStatus.DRAFT, result.getContent().getFirst().getStatus());
        verify(courses).findByInstructorId(owner, pageable);
        verify(courses, never()).findByStatus(any(), any());
        verifyNoInteractions(enrollment);
    }
}
