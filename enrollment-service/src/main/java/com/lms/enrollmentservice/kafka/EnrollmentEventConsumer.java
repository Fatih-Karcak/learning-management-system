package com.lms.enrollmentservice.kafka;

import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventConsumer {

    private final EnrollmentService enrollmentService;

    @KafkaListener(
            topics = "payment-completed",
            groupId = "enrollment-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.info("Ödeme tamamlandı eventi alındı - Topic: {}, StudentId: {}, CourseId: {}",
                topic, event.studentId(), event.courseId());

        try {
            enrollmentService.createEnrollmentFromPayment(event);
        } catch (Exception e) {
            log.error("Enrollment oluşturma hatası: {}", e.getMessage(), e);
            // Dead Letter Queue'ya gönderilecek (Aşama 4'te ekleyeceğiz)
        }
    }
}