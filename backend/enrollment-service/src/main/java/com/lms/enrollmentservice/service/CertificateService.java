package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.client.UserClient;
import com.lms.enrollmentservice.domain.entity.Certificate;
import com.lms.enrollmentservice.domain.enums.EnrollmentStatus;
import com.lms.enrollmentservice.dto.response.CertificateResponse;
import com.lms.enrollmentservice.dto.response.UserInfoResult;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.repository.CertificateRepository;
import com.lms.enrollmentservice.repository.EnrollmentRepository;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserClient userClient;
    private final StorageService storageService;
    private final TemplateEngine templateEngine;
    private final OutboxService outboxService;

    @Transactional
    public void issueForEnrollment(UUID enrollmentId) {

        var enrollment = enrollmentRepository
                .findByIdForUpdate(enrollmentId)
                .orElseThrow(() ->
                        new EnrollmentNotFoundException(
                                "Enrollment not found"
                        )
                );

        if (enrollment.getStatus()
                != EnrollmentStatus.COMPLETED) {
            return;
        }

        var existing = certificateRepository
                .findByEnrollmentId(enrollmentId);

        if (existing.isPresent()
                && existing.get().getPdfStorageKey() != null) {
            return;
        }

        Certificate certificate = existing.orElseGet(() ->
                Certificate.builder()
                        .verificationCode(
                                UUID.randomUUID()
                                        .toString()
                                        .replace("-", "")
                                        .toUpperCase()
                        )
                        .enrollment(enrollment)
                        .studentId(enrollment.getStudentId())
                        .studentName(
                                fetchStudentName(
                                        enrollment.getStudentId()
                                )
                        )
                        .courseId(enrollment.getCourseId())
                        .courseTitle(enrollment.getCourseTitle())
                        .instructorName(
                                enrollment.getInstructorName()
                        )
                        .build()
        );

        certificate = certificateRepository
                .saveAndFlush(certificate);

        byte[] pdf = generatePdf(certificate);

        if (pdf.length == 0) {
            throw new IllegalStateException(
                    "An empty certificate was generated"
            );
        }

        String storageKey =
                "certificates/" + certificate.getId() + ".pdf";

        storageService.upload(
                storageKey,
                pdf,
                "application/pdf"
        );

        certificate.setPdfStorageKey(storageKey);
        certificateRepository.save(certificate);

        outboxService.enqueue(
                "certificate-issued",
                certificate.getId().toString(),
                new CertificateIssuedEvent(
                        certificate.getId(),
                        enrollment.getStudentId(),
                        enrollment.getCourseTitle(),
                        certificate.getVerificationCode()
                )
        );
    }

    @Transactional(readOnly = true)
    public CertificateResponse getCertificate(
            UUID enrollmentId,
            UUID studentId) {

        Certificate certificate = certificateRepository
                .findByEnrollmentId(enrollmentId)
                .orElseThrow(() ->
                        new EnrollmentNotFoundException(
                                "Certificate not found"
                        )
                );

        if (!certificate.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException(
                    "Certificate not found"
            );
        }

        if (certificate.getEnrollment().getStatus()
                != EnrollmentStatus.COMPLETED) {

            throw new EnrollmentNotFoundException(
                    "The certificate is not valid"
            );
        }

        return toResponse(certificate, true);
    }

    @Transactional(readOnly = true)
    public CertificateResponse verifyCertificate(
            String code) {

        Certificate certificate = certificateRepository
                .findByVerificationCode(code)
                .orElseThrow(() ->
                        new EnrollmentNotFoundException(
                                "Invalid verification code"
                        )
                );

        if (certificate.getEnrollment().getStatus()
                != EnrollmentStatus.COMPLETED) {

            throw new EnrollmentNotFoundException(
                    "The certificate is not valid"
            );
        }

        // Public endpoint PDF indirme bağlantısı döndürmez.
        return toResponse(certificate, false);
    }

    private CertificateResponse toResponse(
            Certificate certificate,
            boolean includePdfUrl) {

        String pdfUrl = null;

        if (includePdfUrl
                && certificate.getPdfStorageKey() != null) {

            pdfUrl = storageService.presignedGetUrl(
                    certificate.getPdfStorageKey(),
                    900
            );
        }

        return CertificateResponse.builder()
                .id(certificate.getId())
                .verificationCode(
                        certificate.getVerificationCode()
                )
                .studentName(certificate.getStudentName())
                .courseTitle(certificate.getCourseTitle())
                .instructorName(
                        certificate.getInstructorName()
                )
                .issuedAt(certificate.getIssuedAt())
                .pdfUrl(pdfUrl)
                .build();
    }

    private String fetchStudentName(UUID studentId) {

        UserInfoResult result =
                userClient.getUserById(studentId);

        if (result == null
                || !result.success()
                || result.data() == null
                || result.data().firstName() == null
                || result.data().lastName() == null) {

            throw new IllegalStateException(
                    "The learner name could not be retrieved"
            );
        }

        return result.data().firstName()
                + " "
                + result.data().lastName();
    }

    private byte[] generatePdf(Certificate certificate) {

        try {
            Context context = new Context();

            context.setVariable(
                    "studentName",
                    certificate.getStudentName()
            );

            context.setVariable(
                    "courseTitle",
                    certificate.getCourseTitle()
            );

            context.setVariable(
                    "instructorName",
                    certificate.getInstructorName()
            );

            context.setVariable(
                    "verificationCode",
                    certificate.getVerificationCode()
            );

            context.setVariable(
                    "issuedAt",
                    certificate.getIssuedAt() == null
                            ? ""
                            : certificate.getIssuedAt().format(
                            DateTimeFormatter.ofPattern(
                                    "dd MMMM yyyy", java.util.Locale.ENGLISH
                            )
                    )
            );

            String html = templateEngine.process(
                    "certificate",
                    context
            );

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            PdfRendererBuilder builder =
                    new PdfRendererBuilder();

            builder.useFont(() -> CertificateService.class.getResourceAsStream("/fonts/DejaVuSans.ttf"),
                    "LMS Sans", 400, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> CertificateService.class.getResourceAsStream("/fonts/DejaVuSans-Bold.ttf"),
                    "LMS Sans", 700, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();

            return output.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "The certificate PDF could not be generated",
                    e
            );
        }
    }

    public record CertificateIssuedEvent(
            UUID certificateId,
            UUID studentId,
            String courseTitle,
            String verificationCode
    ) {}
}