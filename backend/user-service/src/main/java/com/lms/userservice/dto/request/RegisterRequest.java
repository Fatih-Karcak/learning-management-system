package com.lms.userservice.dto.request;

import com.lms.userservice.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Ad boş olamaz")
    private String firstName;

    @NotBlank(message = "Soyad boş olamaz")
    private String lastName;

    @Email(message = "Geçerli bir e-posta girin")
    @NotBlank(message = "E-posta boş olamaz")
    private String email;

    @Size(min = 8, message = "Şifre en az 8 karakter olmalı")
    @NotBlank(message = "Şifre boş olamaz")
    private String password;

    private UserRole role = UserRole.STUDENT;
}