package com.lms.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}")
    private String otp;
}
