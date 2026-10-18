package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.RefundRecord;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RefundRecordRepository
        extends JpaRepository<RefundRecord, UUID> {

    @Query("""
        SELECT r FROM RefundRecord r
        JOIN Enrollment e ON e.paymentId = r.paymentId
        WHERE e.status <> 'REFUNDED'
        AND e.status <> 'CANCELLED'
    """)
    List<RefundRecord> findRefundedPaymentsWithOpenEnrollments(
            Pageable pageable);
}