package com.lms.paymentservice.service;

import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.entity.Refund;
import com.lms.paymentservice.domain.enums.PaymentStatus;
import com.lms.paymentservice.domain.enums.RefundStatus;
import com.lms.paymentservice.dto.request.RefundRequest;
import com.lms.paymentservice.dto.response.RefundResponse;
import com.lms.paymentservice.exception.PaymentFailedException;
import com.lms.paymentservice.exception.PaymentNotFoundException;
import com.lms.paymentservice.mapper.PaymentMapper;
import com.lms.paymentservice.repository.PaymentRepository;
import com.lms.paymentservice.repository.RefundRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentMapper paymentMapper;

    // 30 günlük iade politikası
    private static final int REFUND_WINDOW_DAYS = 30;

    @Transactional
    public RefundResponse createRefund(RefundRequest request, UUID studentId) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Ödeme bulunamadı: " + request.getPaymentId()));

        // Yetki kontrolü
        if (!payment.getStudentId().equals(studentId)) {
            throw new PaymentNotFoundException("Bu ödemeye erişim yetkiniz yok");
        }

        // Durum kontrolü
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentFailedException("Sadece tamamlanmış ödemeler iade edilebilir");
        }

        // 30 gün kontrolü
        long daysSincePayment = ChronoUnit.DAYS.between(
                payment.getCompletedAt(), LocalDateTime.now());

        if (daysSincePayment > REFUND_WINDOW_DAYS) {
            throw new PaymentFailedException(
                    "İade süresi dolmuş. İade penceresi: " + REFUND_WINDOW_DAYS + " gün");
        }

        // Stripe'tan iade işle
        com.stripe.model.Refund stripeRefund = processStripeRefund(
                payment.getStripeChargeId(), request.getReason());

        // Refund kaydı oluştur
        Refund refund = Refund.builder()
                .payment(payment)
                .stripeRefundId(stripeRefund.getId())
                .amount(payment.getAmount())
                .reason(request.getReason())
                .status(RefundStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .build();

        refund = refundRepository.save(refund);

        // Payment durumunu güncelle
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // Enrollment Service'i bilgilendir
        kafkaTemplate.send("payment-refunded",
                payment.getId().toString(),
                new RefundCompletedEvent(
                        payment.getId(),
                        payment.getStudentId(),
                        payment.getCourseId(),
                        refund.getId()
                ));

        log.info("İade tamamlandı: Payment={}, Refund={}",
                payment.getId(), refund.getId());

        return paymentMapper.toRefundResponse(refund);
    }

    private com.stripe.model.Refund processStripeRefund(
            String chargeId, String reason) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setCharge(chargeId)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .putMetadata("reason", reason)
                    .build();

            return com.stripe.model.Refund.create(params);

        } catch (StripeException e) {
            log.error("Stripe iade hatası: {}", e.getMessage());
            throw new PaymentFailedException("İade işlemi başarısız: " + e.getMessage());
        }
    }

    public record RefundCompletedEvent(
            UUID paymentId, UUID studentId, UUID courseId, UUID refundId) {}
}