package com.lms.userservice.event;

import java.util.UUID;

// user-service → AuthService.UserRegisteredEvent ile alan uyumlu
public record UserRegisteredEvent(
        UUID userId, String email, String firstName, String verificationToken) {}