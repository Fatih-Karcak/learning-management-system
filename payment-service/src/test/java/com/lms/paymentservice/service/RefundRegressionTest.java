package com.lms.paymentservice.service;

import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.entity.Refund;
import com.lms.paymentservice.domain.enums.PaymentStatus;
import com.lms.paymentservice.dto.request.RefundRequest;
import com.lms.paymentservice.exception.PaymentFailedException;
import com.lms.paymentservice.exception.PaymentNotFoundException;
import com.lms.paymentservice.mapper.PaymentMapper;
import com.lms.paymentservice.repository.PaymentRepository;
import com.lms.paymentservice.repository.RefundRepository;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundRegressionTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final RefundService service = new RefundService(payments, refunds, outbox, mock(PaymentMapper.class));

    private Payment payment() {
        var payment = Payment.builder().id(UUID.randomUUID()).studentId(UUID.randomUUID())
                .courseId(UUID.randomUUID()).status(PaymentStatus.COMPLETED).stripeChargeId("ch_local_test")
                .amount(BigDecimal.TEN).completedAt(LocalDateTime.now().minusDays(1)).build();
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test void repeatedRefundCallsProviderAndEnqueuesEventOnlyOnce() throws Exception {
        var payment = payment();
        var request = new RefundRequest(); request.setPaymentId(payment.getId()); request.setReason("Local regression");
        when(refunds.save(any(Refund.class))).thenAnswer(call -> {
            Refund refund = call.getArgument(0); refund.setId(UUID.randomUUID()); return refund;
        });
        var response = new com.stripe.model.Refund(); response.setId("re_local_test");
        try (var stripe = mockStatic(com.stripe.model.Refund.class)) {
            stripe.when(() -> com.stripe.model.Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(response);
            service.createRefund(request, payment.getStudentId());
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertThrows(PaymentFailedException.class, () -> service.createRefund(request, payment.getStudentId()));
            stripe.verify(() -> com.stripe.model.Refund.create(any(RefundCreateParams.class),
                    argThat((RequestOptions options) -> ("refund-" + payment.getId()).equals(options.getIdempotencyKey()))), times(1));
            verify(outbox, times(1)).enqueue(eq("payment-refunded"), eq(payment.getId().toString()), any());
        }
    }

    @Test void otherStudentCannotRefundPayment() {
        var payment = payment(); var request = new RefundRequest(); request.setPaymentId(payment.getId());
        assertThrows(PaymentNotFoundException.class, () -> service.createRefund(request, UUID.randomUUID()));
        verifyNoInteractions(refunds, outbox);
    }
}
