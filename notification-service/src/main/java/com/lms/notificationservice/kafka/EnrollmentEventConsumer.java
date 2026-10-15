package com.lms.notificationservice.kafka;

import com.lms.notificationservice.event.CertificateIssuedEvent;
import com.lms.notificationservice.event.EnrollmentCreatedEvent;
import com.lms.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "enrollment-created", groupId = "notification-service",
            containerFactory = "enrollmentCreatedContainerFactory")
    public void handleEnrollmentCreated(EnrollmentCreatedEvent event) {
        log.info("Kayıt eventi alındı: {}", event.enrollmentId());
        notificationService.sendEnrollmentEmail(event);
    }

    @KafkaListener(topics = "certificate-issued", groupId = "notification-service",
            containerFactory = "certificateIssuedContainerFactory")
    public void handleCertificateIssued(CertificateIssuedEvent event) {
        log.info("Sertifika eventi alındı: {}", event.certificateId());
        notificationService.sendCertificateEmail(event);
    }
}