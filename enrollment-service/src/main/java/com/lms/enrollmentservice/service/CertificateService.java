package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.client.UserClient;
import com.lms.enrollmentservice.domain.entity.Certificate;
import com.lms.enrollmentservice.domain.entity.Enrollment;
import com.lms.enrollmentservice.dto.response.CertificateResponse;
import com.lms.enrollmentservice.dto.response.UserInfoResult;
import com.lms.enrollmentservice.exception.EnrollmentNotFoundException;
import com.lms.enrollmentservice.repository.CertificateRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TemplateEngine templateEngine;
    private final UserClient userClient;
    private final StorageService storageService;

    @Transactional
    public Certificate generateCertificate(Enrollment enrollment) {
        if (certificateRepository.existsByEnrollmentId(enrollment.getId())) {
            return certificateRepository.findByEnrollmentId(enrollment.getId()).get();
        }

        String verificationCode = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String studentName = fetchStudentName(enrollment.getStudentId());

        Certificate certificate = Certificate.builder()
                .verificationCode(verificationCode)
                .enrollment(enrollment)
                .studentId(enrollment.getStudentId())
                .studentName(studentName)
                .courseId(enrollment.getCourseId())
                .courseTitle(enrollment.getCourseTitle())
                .instructorName(enrollment.getInstructorName())
                .build();

        certificate = certificateRepository.save(certificate);

        byte[] pdfBytes = generatePdf(certificate);
        if (pdfBytes.length > 0) {
            String pdfKey = "certificates/" + certificate.getId() + ".pdf";
            storageService.upload(pdfKey, pdfBytes, "application/pdf");
            certificate.setPdfStorageKey(pdfKey);
            certificate = certificateRepository.save(certificate);
        } else {
            log.warn("PDF oluşturulamadı, pdfStorageKey boş kalacak: {}", certificate.getId());
        }

        kafkaTemplate.send("certificate-issued", certificate.getId().toString(),
                new CertificateIssuedEvent(certificate.getId(), enrollment.getStudentId(),
                        enrollment.getCourseTitle(), verificationCode));

        log.info("Sertifika oluşturuldu: {} - öğrenci: {}", certificate.getId(), studentName);
        return certificate;
    }

    public CertificateResponse getCertificate(UUID enrollmentId, UUID studentId) {
        Certificate cert = certificateRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Sertifika henüz oluşturulmamış"));

        if (!cert.getStudentId().equals(studentId)) {
            throw new EnrollmentNotFoundException("Bu sertifikaya erişim yetkiniz yok");
        }
        return toCertificateResponse(cert);
    }

    public CertificateResponse verifyCertificate(String verificationCode) {
        Certificate cert = certificateRepository.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new EnrollmentNotFoundException("Geçersiz doğrulama kodu"));
        return toCertificateResponse(cert);
    }

    private String fetchStudentName(UUID studentId) {
        try {
            UserInfoResult result = userClient.getUserById(studentId);
            if (result != null && result.data() != null) {
                return result.data().firstName() + " " + result.data().lastName();
            }
        } catch (Exception e) {
            log.warn("Öğrenci adı alınamadı: {} - {}", studentId, e.getMessage());
        }
        return "Öğrenci";
    }

    private byte[] generatePdf(Certificate certificate) {
        try {
            Context ctx = new Context();
            ctx.setVariable("studentName", certificate.getStudentName());
            ctx.setVariable("courseTitle", certificate.getCourseTitle());
            ctx.setVariable("instructorName", certificate.getInstructorName());
            ctx.setVariable("issuedAt", certificate.getIssuedAt() != null
                    ? certificate.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")) : "");
            ctx.setVariable("verificationCode", certificate.getVerificationCode());

            String html = templateEngine.process("certificate", ctx);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF oluşturma hatası: {}", e.getMessage());
            return new byte[0];
        }
    }

    private CertificateResponse toCertificateResponse(Certificate cert) {
        String pdfUrl = cert.getPdfStorageKey() != null
                ? storageService.presignedGetUrl(cert.getPdfStorageKey(), 3600)
                : null;

        return CertificateResponse.builder()
                .id(cert.getId())
                .verificationCode(cert.getVerificationCode())
                .studentName(cert.getStudentName())
                .courseTitle(cert.getCourseTitle())
                .instructorName(cert.getInstructorName())
                .issuedAt(cert.getIssuedAt())
                .pdfUrl(pdfUrl)
                .build();
    }

    public record CertificateIssuedEvent(
            UUID certificateId, UUID studentId, String courseTitle, String verificationCode) {}
}