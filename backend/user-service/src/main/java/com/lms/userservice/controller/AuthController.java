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
    private final com.lms.userservice.service.AccountRecoveryService recovery;
    private final UserService userService; // ← eklendi

    public record Recovery(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email
    ) {}

    public record Reset(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String token,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 12, max = 72)
        String password
    ) {}

    @PostMapping("/forgot-password")
    public ApiResponse<?> forgot(@Valid @RequestBody Recovery r) {
        recovery.request(r.email(), false);
        return ApiResponse.success(null, "Adres uygunsa sıfırlama bağlantısı gönderilecek");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<?> resend(@Valid @RequestBody Recovery r) {
        recovery.request(r.email(), true);
        return ApiResponse.success(null, "Adres uygunsa doğrulama bağlantısı gönderilecek");
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> reset(@Valid @RequestBody Reset r) {
        recovery.reset(r.token(), r.password());
        return ApiResponse.success(null, "Parola yenilendi; tekrar giriş yapın");
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(response, "Kayıt başarılı")
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giriş başarılı"));
    }

    public record Refresh(@jakarta.validation.constraints.NotBlank String refreshToken) {}

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody Refresh request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()), "Oturum yenilendi");
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token); // ← authService yerine userService
        return ResponseEntity.ok(ApiResponse.success(null, "E-posta başarıyla doğrulandı"));
    }
}
