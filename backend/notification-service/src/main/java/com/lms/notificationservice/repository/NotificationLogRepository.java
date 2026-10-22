package com.lms.notificationservice.repository;

import com.lms.notificationservice.domain.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    Page<NotificationLog> findByRecipientUserId(UUID recipientUserId, Pageable pageable);
}