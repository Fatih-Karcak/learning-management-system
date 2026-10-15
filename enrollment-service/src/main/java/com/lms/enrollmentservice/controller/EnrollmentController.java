package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.dto.response.CertificateResponse;
import com.lms.enrollmentservice.dto.response.EnrollmentResponse;
import com.lms.enrollmentservice.service.CertificateService;
import com.lms.enrollmentservice.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final CertificateService certificateService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> getMyEnrollments(
            @RequestHeader("X-User-Id") UUID studentId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                enrollmentService.getStudentEnrollments(studentId, pageable),
                "Başarılı"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getEnrollment(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                enrollmentService.getEnrollment(id, studentId), "Başarılı"));
    }

    @GetMapping("/course/{courseId}/check")
    public ResponseEntity<ApiResponse<Boolean>> checkEnrollment(
            @PathVariable UUID courseId,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                enrollmentService.isEnrolled(studentId, courseId), "Başarılı"));
    }

    @GetMapping("/{id}/certificate")
    public ResponseEntity<ApiResponse<CertificateResponse>> getCertificate(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                certificateService.getCertificate(id, studentId), "Başarılı"));
    }

    @GetMapping("/certificates/verify/{code}")
    public ResponseEntity<ApiResponse<CertificateResponse>> verifyCertificate(
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                certificateService.verifyCertificate(code), "Sertifika geçerli"));
    }
}