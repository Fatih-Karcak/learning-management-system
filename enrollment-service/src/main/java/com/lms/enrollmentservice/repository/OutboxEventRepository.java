package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.OutboxEvent;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.sentAt IS NULL
        ORDER BY e.createdAt
    """)
    List<OutboxEvent> findUnsent(Pageable pageable);
}