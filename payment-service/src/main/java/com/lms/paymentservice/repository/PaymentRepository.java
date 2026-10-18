package com.lms.paymentservice.repository;

import com.lms.paymentservice.domain.entity.Payment;
import com.lms.paymentservice.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByStripePaymentIntentId(String intentId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.stripePaymentIntentId = :intentId")
    Optional<Payment> findByStripePaymentIntentIdForUpdate(String intentId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    Page<Payment> findByStudentId(UUID studentId, Pageable pageable);

    Page<Payment> findByStudentIdAndStatus(
            UUID studentId, PaymentStatus status, Pageable pageable);

    boolean existsByStudentIdAndCourseIdAndStatus(
            UUID studentId, UUID courseId, PaymentStatus status);

    @Query("""
        SELECT SUM(p.amount) FROM Payment p
        WHERE p.courseId = :courseId
        AND p.status = 'COMPLETED'
        """)
    java.math.BigDecimal sumRevenuesByCourse(UUID courseId);
}
