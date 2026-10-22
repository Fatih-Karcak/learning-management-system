package com.lms.notificationservice.service;

import com.lms.notificationservice.client.UserClient;
import com.lms.notificationservice.domain.entity.NotificationLog;
import com.lms.notificationservice.domain.enums.NotificationChannel;
import com.lms.notificationservice.domain.enums.NotificationStatus;
import com.lms.notificationservice.dto.response.UserInfoResponse;
import com.lms.notificationservice.dto.response.UserInfoResult;
import com.lms.notificationservice.event.*;
import com.lms.notificationservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserClient userClient;
    private final EmailService emailService;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl = "";

    public void sendWelcomeEmail(UserRegisteredEvent event) {
        Context ctx = new Context();
        ctx.setVariable("firstName", event.firstName());
        boolean useFrontend = frontendBaseUrl != null && !frontendBaseUrl.isBlank();
        String baseUrl = (useFrontend ? frontendBaseUrl : publicBaseUrl).replaceAll("/+$", "");
        ctx.setVariable("verificationUrl", org.springframework.web.util.UriComponentsBuilder
                .fromUriString(baseUrl)
                .path(useFrontend ? "/verify-email" : "/api/v1/auth/verify-email")
                .queryParam("token", event.verificationToken())
                .build().encode().toUriString());

        sendAndLog(event.userId(), event.email(), "LMS'e Hoş Geldiniz!",
                "welcome-email", ctx, "USER_REGISTERED");
    }

    public void sendCoursePublishedEmail(CoursePublishedEvent event) {
        UserInfoResponse instructor = fetchUser(event.instructorId());
        if (instructor == null) return;

        Context ctx = new Context();
        ctx.setVariable("instructorName", instructor.firstName());
        ctx.setVariable("courseTitle", event.title());

        sendAndLog(event.instructorId(), instructor.email(), "Kursunuz Yayınlandı: " + event.title(),
                "course-published-email", ctx, "COURSE_PUBLISHED");
    }

    public void sendEnrollmentEmail(EnrollmentCreatedEvent event) {
        String email = event.studentEmail();
        String studentName = "";

        if (email == null) {
            UserInfoResponse student = fetchUser(event.studentId());
            if (student == null) return;
            email = student.email();
            studentName = student.firstName();
        }

        Context ctx = new Context();
        ctx.setVariable("studentName", studentName);
        ctx.setVariable("courseTitle", event.courseTitle());

        sendAndLog(event.studentId(), email, "Kursa Kaydınız Tamamlandı: " + event.courseTitle(),
                "course-enrolled-email", ctx, "ENROLLMENT_CREATED");
    }

    public void sendPaymentReceiptEmail(PaymentReceiptEvent event) {
        UserInfoResponse student = fetchUser(event.studentId());
        if (student == null) return;

        Context ctx = new Context();
        ctx.setVariable("studentName", student.firstName());
        ctx.setVariable("courseTitle", event.courseTitle());
        ctx.setVariable("amount", event.amount());
        ctx.setVariable("instructorName", event.instructorName());

        sendAndLog(event.studentId(), student.email(), "Ödeme Makbuzu: " + event.courseTitle(),
                "payment-receipt-email", ctx, "PAYMENT_RECEIPT");
    }

    public void sendCertificateEmail(CertificateIssuedEvent event) {
        UserInfoResponse student = fetchUser(event.studentId());
        if (student == null) return;

        Context ctx = new Context();
        ctx.setVariable("studentName", student.firstName());
        ctx.setVariable("courseTitle", event.courseTitle());
        ctx.setVariable("verificationCode", event.verificationCode());

        sendAndLog(event.studentId(), student.email(), "Tebrikler! Sertifikanız Hazır: " + event.courseTitle(),
                "certificate-issued-email", ctx, "CERTIFICATE_ISSUED");
    }

    public Page<NotificationLog> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationLogRepository.findByRecipientUserId(userId, pageable);
    }

    // ── Yardımcılar ──

    private UserInfoResponse fetchUser(UUID userId) {
        try {
            UserInfoResult result = userClient.getUserById(userId);
            return result != null ? result.data() : null;
        } catch (Exception e) {
            log.error("Kullanıcı bilgisi alınamadı: {} - {}", userId, e.getMessage());
            return null;
        }
    }

    // Hata olsa bile @KafkaListener'a asla exception fırlatmıyoruz —
    // yoksa Kafka aynı mesajı defalarca yeniden dener (redelivery loop).
    private void sendAndLog(UUID recipientUserId, String email, String subject,
                            String template, Context ctx, String eventType) {
        NotificationLog logEntry = NotificationLog.builder()
                .recipientUserId(recipientUserId)
                .recipientEmail(email)
                .channel(NotificationChannel.EMAIL)
                .subject(subject)
                .eventType(eventType)
                .build();

        try {
            emailService.sendHtmlEmail(email, subject, template, ctx);
            logEntry.setStatus(NotificationStatus.SENT);
        } catch (Exception e) {
            log.error("Bildirim gönderilemedi - tip: {}, alıcı: {}, hata: {}",
                    eventType, email, e.getMessage());
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setFailureReason(e.getMessage());
        }

        notificationLogRepository.save(logEntry);
    }
}
