package com.lms.paymentservice.service;

import com.lms.paymentservice.client.CourseClient;
import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.enums.PaymentStatus;
import com.lms.paymentservice.dto.request.CreatePaymentIntentRequest;
import com.lms.paymentservice.dto.response.*;
import com.lms.paymentservice.fraud.*;
import com.lms.paymentservice.mapper.PaymentMapper;
import com.lms.paymentservice.repository.PaymentRepository;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRegressionTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CourseClient courses = mock(CourseClient.class);
    private final FraudDetectionService fraud = mock(FraudDetectionService.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final PaymentService service = new PaymentService(payments, courses, fraud, outbox, mock(PaymentMapper.class));

    private Payment payment(PaymentStatus status) {
        var payment = Payment.builder().id(UUID.randomUUID()).studentId(UUID.randomUUID())
                .courseId(UUID.randomUUID()).amount(new BigDecimal("100"))
                .stripePaymentIntentId("pi_local_test").status(status).build();
        when(payments.findByStripePaymentIntentIdForUpdate("pi_local_test")).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test void duplicateSuccessEnqueuesEachEventOnce() {
        var payment = payment(PaymentStatus.PENDING);
        service.handlePaymentSucceeded("pi_local_test", "ch_local_test", null);
        service.handlePaymentSucceeded("pi_local_test", "ch_local_test", null);
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        verify(payments, times(1)).save(payment);
        verify(outbox, times(1)).enqueue(eq("payment-completed"), eq(payment.getId().toString()), any());
        verify(outbox, times(1)).enqueue(eq("payment-receipt"), eq(payment.getId().toString()), any());
    }

    @Test void lateSuccessCannotOverwriteTerminalState() {
        for (PaymentStatus state : new PaymentStatus[]{PaymentStatus.REFUNDED,
                PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.CANCELLED, PaymentStatus.COMPLETED}) {
            var payment = payment(state);
            service.handlePaymentSucceeded("pi_local_test", "ch_local_test", null);
            assertEquals(state, payment.getStatus());
        }
        verifyNoInteractions(outbox);
        verify(payments, never()).save(any());
    }

    @Test void lateFailureCannotOverwriteCompletedOrRefundedState() {
        for (PaymentStatus state : new PaymentStatus[]{PaymentStatus.COMPLETED, PaymentStatus.REFUNDED}) {
            var payment = payment(state);
            service.handlePaymentFailed("pi_local_test", "old failure");
            assertEquals(state, payment.getStatus());
        }
        verify(payments, never()).save(any());
    }

    @Test void resolvedCoursePriceReachesStripeSdkAndCreatesPendingPayment() throws Exception {
        UUID courseId = UUID.randomUUID(); UUID studentId = UUID.randomUUID();
        when(courses.getCoursePrice(courseId)).thenReturn(new CoursePriceResult(true,
                new CoursePriceResponse(courseId, "Test Course", null, "Instructor",
                        new BigDecimal("100.00"), new BigDecimal("80.00"), "PUBLISHED")));
        when(fraud.checkAmountRisk(eq(studentId), any(), eq("try"))).thenReturn(FraudResult.safe());
        when(fraud.checkVelocity(studentId)).thenReturn(FraudResult.safe());
        when(payments.save(any())).thenAnswer(call -> {
            Payment payment = call.getArgument(0); payment.setId(UUID.randomUUID()); return payment;
        });
        var intent = new PaymentIntent(); intent.setId("pi_local_test"); intent.setClientSecret("local-test-only");
        try (var stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);
            var request = new CreatePaymentIntentRequest(); request.setCourseId(courseId);
            var response = service.createPaymentIntent(request, studentId);
            assertEquals(new BigDecimal("80.00"), response.getAmount());
            assertNotNull(response.getPaymentId());
            stripe.verify(() -> PaymentIntent.create(argThat((PaymentIntentCreateParams params) ->
                    params.getAmount() == 8000L && "try".equals(params.getCurrency()))));
        }
    }
}
