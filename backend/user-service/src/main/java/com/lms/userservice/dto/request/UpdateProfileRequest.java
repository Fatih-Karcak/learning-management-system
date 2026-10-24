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

    @Size(min = 12, max = 72, message = "Password must contain 12–72 characters")
    private String newPassword;
    private String currentPassword;
}