package com.lms.paymentservice.domain.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "text"
    )
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public OutboxEvent(
            String topic,
            String eventKey,
            String payload) {

        this.id = UUID.randomUUID();
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() {
        this.sentAt = LocalDateTime.now();
    }
}