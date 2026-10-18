package com.lms.paymentservice.service;

import com.lms.paymentservice.client.CourseClient;
import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.enums.PaymentStatus;
import com.lms.paymentservice.dto.request.CreatePaymentIntentRequest;
import com.lms.paymentservice.dto.response.CoursePriceResponse;
import com.lms.paymentservice.dto.response.PaymentIntentResponse;
import com.lms.paymentservice.dto.response.PaymentResponse;
import com.lms.paymentservice.event.PaymentCompletedEvent;
import com.lms.paymentservice.exception.PaymentFailedException;
import com.lms.paymentservice.exception.PaymentNotFoundException;
import com.lms.paymentservice.fraud.FraudDetectionService;
import com.lms.paymentservice.fraud.FraudResult;
import com.lms.paymentservice.mapper.PaymentMapper;
import com.lms.paymentservice.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CourseClient courseClient;
    private final FraudDetectionService fraudDetectionService;
    private final OutboxService outboxService;
    private final PaymentMapper paymentMapper;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            CreatePaymentIntentRequest request, UUID studentId) {

        if (paymentRepository.existsByStudentIdAndCourseIdAndStatus(
                studentId, request.getCourseId(), PaymentStatus.COMPLETED)) {
            throw new PaymentFailedException("Bu kursa zaten ödeme yapılmış");
        }

        // ── ADIM 1: SIRALI ──
        // Kurs fiyatını mutlaka önce almalıyız çünkü sonraki fraud
        // kontrolü buna bağımlı. Bu adımı paralelleştirmek anlamsız
        // ve yanlıştı (önceki halde placeholder 999 kullanılıyordu).
        com.lms.paymentservice.dto.response.CoursePriceResult result;
        try {
            result = courseClient.getCoursePrice(request.getCourseId());
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new PaymentNotFoundException("Kurs bulunamadı");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Kurs servisine ulaşılamadı", ex);
        }
        if (result == null || !result.success() || result.data() == null
                || !request.getCourseId().equals(result.data().id())
                || !"PUBLISHED".equals(result.data().status()) || result.data().price() == null) {
            throw new PaymentFailedException("Satın alınabilir kurs bilgisi bulunamadı");
        }
        CoursePriceResponse course = result.data();
        BigDecimal amount = course.effectivePrice();
        if (amount.signum() < 0 || amount.compareTo(course.price()) > 0) {
            throw new PaymentFailedException("Kurs fiyatı geçersiz");
        }

        // ── ADIM 2: GERÇEK PARALEL İŞ ──
        // Şimdi elimizde gerçek tutar var. Tutar bazlı kural kontrolü
        // ile öğrencinin işlem sıklığı kontrolü birbirinden BAĞIMSIZ,
        // bu yüzden Java 25 Structured Concurrency burada anlamlı.
        FraudResult amountCheck;
        FraudResult velocityCheck;

        try (var scope = StructuredTaskScope.open()) {

            StructuredTaskScope.Subtask<FraudResult> amountTask = scope.fork(() ->
                    fraudDetectionService.checkAmountRisk(studentId, amount, "try"));

            StructuredTaskScope.Subtask<FraudResult> velocityTask = scope.fork(() ->
                    fraudDetectionService.checkVelocity(studentId));

            scope.join();   // JDK 25: tüm subtask'lar başarılı olmalı, yoksa FailedException fırlatır

            amountCheck = amountTask.get();
            velocityCheck = velocityTask.get();

        } catch (StructuredTaskScope.FailedException e) {
            throw new PaymentFailedException("Fraud kontrolü başarısız: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentFailedException("Ödeme işlemi kesintiye uğradı");
        }
        FraudResult fraudResult = FraudResult.combine(amountCheck, velocityCheck);

        if (!fraudResult.passed()) {
            log.warn("Fraud tespiti - StudentId: {}, Risk: {}, Sebep: {}",
                    studentId, fraudResult.riskScore(), fraudResult.reason());
            throw new PaymentFailedException("Ödeme güvenlik kontrolünden geçemedi");
        }

        // ── ADIM 3: Stripe + DB kaydı (öncekiyle aynı) ──
        PaymentIntent intent = createStripeIntent(amount, course.title(), studentId);

        Payment payment = Payment.builder()
                .studentId(studentId)
                .courseId(request.getCourseId())
                .courseTitle(course.title())
                .courseThumbnailUrl(course.thumbnailUrl())
                .instructorName(course.instructorName())
                .stripePaymentIntentId(intent.getId())
                .amount(amount)
                .currency("try")
                .status(PaymentStatus.PENDING)
                .fraudCheckPassed(fraudResult.passed())
                .build();

        payment = paymentRepository.save(payment);
        log.info("PaymentIntent oluşturuldu: {}", payment.getId());

        return PaymentIntentResponse.builder()
                .paymentId(payment.getId())
                .clientSecret(intent.getClientSecret())
                .amount(amount)
                .currency("TRY")
                .courseTitle(course.title())
                .build();
    }

    // Stripe Webhook'tan çağrılır
    @Transactional
    public void handlePaymentSucceeded(String stripeIntentId, String chargeId,
                                       String receiptUrl) {
        Payment payment = paymentRepository
                .findByStripePaymentIntentIdForUpdate(stripeIntentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Ödeme bulunamadı: " + stripeIntentId));

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PROCESSING
                && payment.getStatus() != PaymentStatus.FAILED) {
            return;
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setStripeChargeId(chargeId);
        payment.setReceiptUrl(receiptUrl);
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Enrollment Service e bildir
        outboxService.enqueue("payment-completed",
                payment.getId().toString(),
                new PaymentCompletedEvent(
                        payment.getId(),
                        payment.getStudentId(),
                        payment.getCourseId(),
                        payment.getCourseTitle(),
                        payment.getCourseThumbnailUrl(),
                        payment.getInstructorName(),
                        payment.getAmount()
                ));

        // Notification Service e bildir
        outboxService.enqueue("payment-receipt",
                payment.getId().toString(),
                new PaymentCompletedEvent(
                        payment.getId(),
                        payment.getStudentId(),
                        payment.getCourseId(),
                        payment.getCourseTitle(),
                        payment.getCourseThumbnailUrl(),
                        payment.getInstructorName(),
                        payment.getAmount()
                ));

        log.info("Ödeme tamamlandı: {} - Tutar: {} TRY",
                payment.getId(), payment.getAmount());
    }

    @Transactional
    public void handlePaymentFailed(String stripeIntentId, String failureReason) {
        paymentRepository.findByStripePaymentIntentIdForUpdate(stripeIntentId)
                .ifPresent(payment -> {
                    if (payment.getStatus() != PaymentStatus.PENDING
                            && payment.getStatus() != PaymentStatus.PROCESSING) return;
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason(failureReason);
                    paymentRepository.save(payment);
                    log.warn("Ödeme başarısız: {} - {}", payment.getId(), failureReason);
                });
    }

    public PaymentResponse getPayment(UUID paymentId, UUID studentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Ödeme bulunamadı: " + paymentId));

        if (!payment.getStudentId().equals(studentId)) {
            throw new PaymentNotFoundException("Bu ödemeye erişim yetkiniz yok");
        }

        return paymentMapper.toResponse(payment);
    }

    public Page<PaymentResponse> getStudentPayments(UUID studentId, Pageable pageable) {
        return paymentRepository.findByStudentId(studentId, pageable)
                .map(paymentMapper::toResponse);
    }

    private PaymentIntent createStripeIntent(
            BigDecimal amount, String courseTitle, UUID studentId) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency("try")
                    .setDescription("LMS Kurs Satın Alma: " + courseTitle)
                    .putMetadata("studentId", studentId.toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            return PaymentIntent.create(params);

        } catch (StripeException e) {
            log.error("Stripe hatası: {}", e.getMessage());
            throw new PaymentFailedException("Stripe bağlantı hatası: " + e.getMessage());
        }
    }
}
