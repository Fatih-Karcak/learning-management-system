package com.lms.enrollmentservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "processed_refunds")
@Getter
@NoArgsConstructor
public class RefundRecord {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "refund_id", nullable = false)
    private UUID refundId;

    public RefundRecord(
            UUID paymentId,
            UUID studentId,
            UUID courseId,
            UUID refundId) {

        this.paymentId = paymentId;
        this.studentId = studentId;
        this.courseId = courseId;
        this.refundId = refundId;
    }
}