package com.lms.notificationservice.domain.entity;
import com.lms.notificationservice.domain.enums.NotificationChannel;
import com.lms.notificationservice.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "notification_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID recipientUserId;
    private String recipientEmail;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    private String subject;
    @Column(nullable = false)
    private String eventType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    private String failureReason;
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;




}
