package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourseAccessPolicy {

    private final EnrollmentClient enrollmentClient;
    private final CourseAuthoringAccess authors;
    private final LearningPolicyService learning;

    public void requireVisible(Course course, AuthenticatedUser viewer) {
        if (
            course.getStatus() != CourseStatus.PUBLISHED &&
            !isOwnerOrAdmin(course, viewer) &&
            !(viewer != null && isEnrolled(course.getId(), viewer.id()))
        ) {
            throw new CourseNotFoundException("Course not found");
        }
    }

    public boolean canReadContent(Course course, AuthenticatedUser viewer) {
        return (
            isOwnerOrAdmin(course, viewer) ||
            (viewer != null && isEnrolled(course.getId(), viewer.id()))
        );
    }

    public void requireEnrolled(UUID courseId, UUID studentId) {
        if (!isEnrolled(courseId, studentId)) {
            throw new UnauthorizedException(
                "You must be enrolled in this course to perform this action"
            );
        }
    }

    public void requireLessonAccess(Course course,UUID lesson,AuthenticatedUser user) {
        if(isOwnerOrAdmin(course,user)) return;
        String reason=learning.lockReason(course,lesson,user.id());
        if(reason!=null) throw new UnauthorizedException(reason);
    }

    private boolean isEnrolled(UUID courseId, UUID studentId) {
        try {
            EnrollmentClient.Result result = enrollmentClient.check(
                courseId,
                studentId
            );
            if (result == null || !result.success() || result.data() == null) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Enrollment details could not be verified"
                );
            }
            return result.data();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The enrollment service could not be reached",
                ex
            );
        }
    }

    private boolean isOwnerOrAdmin(Course course, AuthenticatedUser viewer) {
        return (
            viewer != null &&
            (viewer.isAdmin() ||
                course.getInstructorId().equals(viewer.id()) ||
                authors.canEdit(course, viewer.id()))
        );
    }

    public void filterLessons(
        List<LessonResponse> lessons,
        boolean fullAccess
    ) {
        if (lessons == null || fullAccess) return;
        for (LessonResponse lesson : lessons) {
            if (!lesson.isPreviewAllowed()) {
                lesson.setContent(null);
                lesson.setVideoUrl(null);
            }
        }
    }

    public void filterCourseLessons(Course course,List<LessonResponse> lessons,boolean fullAccess,AuthenticatedUser viewer) {
        filterLessons(lessons,fullAccess);
        if(!fullAccess||viewer==null||isOwnerOrAdmin(course,viewer)||lessons==null) return;
        var locks=learning.lockReasons(course,viewer.id());
        for(var lesson:lessons) {
            String reason=locks.get(lesson.getId());
            lesson.setLocked(reason!=null);lesson.setLockReason(reason);
            if(reason!=null) { lesson.setContent(null);lesson.setVideoUrl(null); }
        }
    }
}
