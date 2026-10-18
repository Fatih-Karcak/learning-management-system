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

    public CourseSummaryResponse requireCourse(
            UUID courseId) {

        CourseEnvelope response =
                courseClient.getCourseById(courseId);

        if (response == null
                || !response.success()
                || response.data() == null
                || !courseId.equals(response.data().id())) {

            throw new IllegalStateException(
                    "Course Service geçerli kurs bilgisi döndürmedi"
            );
        }

        return response.data();
    }

    public int getTotalLessons(UUID courseId) {

        int total = requireCourse(courseId)
                .totalLessons();

        if (total <= 0) {
            throw new IllegalStateException(
                    "Kursun ders sayısı hesaplanamadı"
            );
        }

        return total;
    }
}