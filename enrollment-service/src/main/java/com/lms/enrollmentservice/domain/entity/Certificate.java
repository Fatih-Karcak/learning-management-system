package com.lms.enrollmentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // QR ile doğrulama için benzersiz kod
    @Column(nullable = false, unique = true)
    private String verificationCode;
    private String pdfStorageKey;   // kalıcı; pdfUrl artık DB'de tutulmuyor, her seferinde üretiliyor
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    private UUID studentId;
    private String studentName;
    private UUID courseId;
    private String courseTitle;
    private String instructorName;

    private String pdfUrl;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime issuedAt;
}