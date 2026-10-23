package com.lms.enrollmentservice.kafka;

import com.lms.enrollmentservice.event.PaymentCompletedEvent;
import com.lms.enrollmentservice.event.RefundCompletedEvent;
import com.lms.enrollmentservice.service.EnrollmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
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
            PaymentCompletedEvent event) {

        log.info(
                "Payment event alındı: {}",
                event.paymentId()
        );

        enrollmentService.createEnrollmentFromPayment(event);
    }

    @KafkaListener(
            topics = "payment-refunded",
            groupId = "enrollment-service",
            containerFactory = "refundKafkaListenerContainerFactory"
    )
    public void handleRefundCompleted(
            RefundCompletedEvent event) {

        log.info(
                "Refund event alındı: {}",
                event.paymentId()
        );

        enrollmentService.handleRefund(event);
    }
}