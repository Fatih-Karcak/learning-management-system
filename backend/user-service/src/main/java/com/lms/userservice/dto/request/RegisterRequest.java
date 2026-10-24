package com.lms.userservice.dto.request;

import com.lms.userservice.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Enter a valid email address")
    @NotBlank(message = "Email address is required")
    private String email;

    @Size(min = 12, max = 72, message = "Password must contain 12–72 characters")
    @NotBlank(message = "Password is required")
    private String password;

    private UserRole role = UserRole.STUDENT;
}
