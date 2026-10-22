package com.lms.userservice.dto.request;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String bio;
    private String phoneNumber;
    private String avatarUrl;

    @Size(min = 8, message = "Şifre en az 8 karakter olmalı")
    private String newPassword;
    private String currentPassword;
}