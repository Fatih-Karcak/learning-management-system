package com.lms.paymentservice.domain.entity;

import com.lms.paymentservice.domain.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    private String stripeRefundId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}