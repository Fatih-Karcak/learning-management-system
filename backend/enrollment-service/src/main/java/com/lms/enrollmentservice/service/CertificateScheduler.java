package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.repository.EnrollmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateScheduler {

    private final EnrollmentRepository enrollmentRepository;
    private final CertificateService certificateService;

    @Scheduled(
            fixedDelayString =
                    "${enrollment.certificate-interval-ms:30000}"
    )
    public void generatePendingCertificates() {

        var enrollments =
                enrollmentRepository.findMissingCertificates(
                        EnrollmentStatus.COMPLETED,
                        PageRequest.of(0, 25)
                );

        for (var enrollment : enrollments) {

            try {
                certificateService.issueForEnrollment(
                        enrollment.getId()
                );

            } catch (Exception e) {

                log.error(
                        "Sertifika üretimi başarısız: {}",
                        enrollment.getId(),
                        e
                );
            }
        }
    }
}