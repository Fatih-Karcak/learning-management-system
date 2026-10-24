package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.client.CourseClient;
import com.lms.enrollmentservice.dto.response.CourseEnvelope;
import com.lms.enrollmentservice.dto.response.CourseSummaryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseCatalogService {

    private final CourseClient courseClient;

    public void requireLessonAccess(UUID course,UUID lesson,UUID student) {
        var response=courseClient.access(course,lesson,student);
        if(response==null||!response.success()||response.data()==null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Learning access could not be verified");
        if(!response.data()) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,"Complete prerequisite lessons or wait for the release date");
    }

    public CourseSummaryResponse requireCourse(
            UUID courseId) {

        CourseEnvelope response =
                courseClient.getCourseById(courseId);

        if (response == null
                || !response.success()
                || response.data() == null
                || !courseId.equals(response.data().id())) {

            throw new IllegalStateException(
                    "The course service returned invalid course details"
            );
        }

        return response.data();
    }

    public int getTotalLessons(UUID courseId) {

        int total = requireCourse(courseId)
                .totalLessons();

        if (total <= 0) {
            throw new IllegalStateException(
                    "The course lesson count could not be calculated"
            );
        }

        return total;
    }
}
