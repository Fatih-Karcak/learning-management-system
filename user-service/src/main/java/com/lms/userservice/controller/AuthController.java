package com.lms.userservice.controller;

import com.lms.userservice.dto.request.LoginRequest;
import com.lms.userservice.dto.request.RegisterRequest;
import com.lms.userservice.dto.response.ApiResponse;
import com.lms.userservice.dto.response.AuthResponse;
import com.lms.userservice.service.AuthService;
import com.lms.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;   // ← eklendi

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Kayıt başarılı"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giriş başarılı"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);   // ← authService yerine userService
        return ResponseEntity.ok(ApiResponse.success(null, "E-posta başarıyla doğrulandı"));
    }




}