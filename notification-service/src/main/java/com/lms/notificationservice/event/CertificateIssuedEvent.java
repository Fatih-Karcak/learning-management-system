package com.lms.notificationservice.event;

import java.util.UUID;

public record CertificateIssuedEvent(
        UUID certificateId,
        UUID studentId,
        String courseTitle,
        String verificationCode
) {
}
