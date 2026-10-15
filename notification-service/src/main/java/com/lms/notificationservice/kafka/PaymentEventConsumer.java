package com.lms.notificationservice.kafka;

import com.lms.notificationservice.event.PaymentReceiptEvent;
import com.lms.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "payment-receipt", groupId = "notification-service",
            containerFactory = "paymentReceiptContainerFactory")
    public void handlePaymentReceipt(PaymentReceiptEvent event) {
        log.info("Ödeme makbuzu eventi alındı: {}", event.paymentId());
        notificationService.sendPaymentReceiptEmail(event);
    }
}