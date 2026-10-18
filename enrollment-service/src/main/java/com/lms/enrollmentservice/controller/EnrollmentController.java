package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.*;
import com.lms.enrollmentservice.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final CertificateService certificateService;

    private UUID currentStudentId(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>>
    getMyEnrollments(
            Authentication auth,
            Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        enrollmentService.getStudentEnrollments(
                                currentStudentId(auth),
                                pageable
                        ),
                        "Başarılı"
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>>
    getEnrollment(
            @PathVariable UUID id,
            Authentication auth) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        enrollmentService.getEnrollment(
                                id,
                                currentStudentId(auth)
                        ),
                        "Başarılı"
                )
        );
    }

    @GetMapping("/course/{courseId}/check")
    public ResponseEntity<ApiResponse<Boolean>>
    checkEnrollment(
            @PathVariable UUID courseId,
            Authentication auth) {

        boolean enrolled = enrollmentService.isEnrolled(
                currentStudentId(auth),
                courseId
        );

        return ResponseEntity.ok(
                ApiResponse.success(enrolled, "Başarılı")
        );
    }

    @GetMapping("/{id}/certificate")
    public ResponseEntity<ApiResponse<CertificateResponse>>
    getCertificate(
            @PathVariable UUID id,
            Authentication auth) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        certificateService.getCertificate(
                                id,
                                currentStudentId(auth)
                        ),
                        "Başarılı"
                )
        );
    }

    @GetMapping("/certificates/verify/{code}")
    public ResponseEntity<ApiResponse<CertificateResponse>>
    verifyCertificate(@PathVariable String code) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        certificateService.verifyCertificate(code),
                        "Sertifika doğrulandı"
                )
        );
    }
}