package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.repository.RefundRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciliationScheduler {

    private final RefundRecordRepository refundRepository;
    private final EnrollmentService enrollmentService;

    @Scheduled(
            fixedDelayString =
                    "${enrollment.refund-reconciliation-interval-ms:30000}"
    )
    public void reconcile() {

        var refunds =
                refundRepository
                        .findRefundedPaymentsWithOpenEnrollments(
                                PageRequest.of(0, 100)
                        );

        for (var refund : refunds) {

            try {
                enrollmentService.reconcileRefund(refund);

            } catch (Exception e) {

                log.error(
                        "İade mutabakatı başarısız: {}",
                        refund.getPaymentId(),
                        e
                );
            }
        }
    }
}