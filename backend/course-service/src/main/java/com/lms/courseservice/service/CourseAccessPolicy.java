package com.lms.courseservice.service;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.domain.entity.Course;
import com.lms.courseservice.domain.enums.CourseStatus;
import com.lms.courseservice.dto.response.LessonResponse;
import com.lms.courseservice.exception.CourseNotFoundException;
import com.lms.courseservice.exception.UnauthorizedException;
import com.lms.courseservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseAccessPolicy {
    private final EnrollmentClient enrollmentClient;

    public void requireVisible(Course course, AuthenticatedUser viewer) {
        if (course.getStatus() != CourseStatus.PUBLISHED && !isOwnerOrAdmin(course, viewer)
                && !(viewer!=null && isEnrolled(course.getId(),viewer.id()))) {
            throw new CourseNotFoundException("Kurs bulunamadı");
        }
    }

    public boolean canReadContent(Course course, AuthenticatedUser viewer) {
        return isOwnerOrAdmin(course, viewer)
                || viewer != null && isEnrolled(course.getId(), viewer.id());
    }

    public void requireEnrolled(UUID courseId, UUID studentId) {
        if (!isEnrolled(courseId, studentId)) {
            throw new UnauthorizedException("Bu işlem için kursa kayıtlı olmalısınız");
        }
    }

    private boolean isEnrolled(UUID courseId, UUID studentId) {
        try {
            EnrollmentClient.Result result = enrollmentClient.check(courseId, studentId);
            if (result == null || !result.success() || result.data() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kayıt bilgisi doğrulanamadı");
            }
            return result.data();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kayıt servisine ulaşılamadı", ex);
        }
    }

    private boolean isOwnerOrAdmin(Course course, AuthenticatedUser viewer) {
        return viewer != null && (viewer.isAdmin() || course.getInstructorId().equals(viewer.id()));
    }

    public void filterLessons(List<LessonResponse> lessons, boolean fullAccess) {
        if (lessons == null || fullAccess) return;
        for (LessonResponse lesson : lessons) {
            if (!lesson.isPreviewAllowed()) {
                lesson.setContent(null);
                lesson.setVideoUrl(null);
            }
        }
    }
}
