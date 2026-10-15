package com.lms.userservice.dto.response;

import com.lms.userservice.domain.enums.UserRole;
import com.lms.userservice.domain.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String bio;
    private String avatarUrl;
    private String phoneNumber;
    private UserRole role;
    private UserStatus status;
    private boolean emailVerified;
    private LocalDateTime createdAt;
}