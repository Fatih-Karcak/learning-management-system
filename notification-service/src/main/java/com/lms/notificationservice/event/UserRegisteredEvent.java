package com.lms.notificationservice.event;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String firstName,
        String verificationToken

) {
}
