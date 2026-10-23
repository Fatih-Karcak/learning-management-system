package com.lms.userservice.service;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.UserRole;
import com.lms.userservice.domain.enums.UserStatus;
import com.lms.userservice.dto.request.LoginRequest;
import com.lms.userservice.dto.request.RegisterRequest;
import com.lms.userservice.dto.response.AuthResponse;
import com.lms.userservice.dto.response.UserResponse;
import com.lms.userservice.exception.EmailAlreadyExistsException;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import com.lms.userservice.security.JwtTokenProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final SessionService sessions;
    private final MfaService mfa;
    private final LoginThrottleService throttle;
    private final PlatformEvents events;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() == null || request.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Bu rol herkese açık kayıtta kullanılamaz");
        }
        request.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Bu e-posta adresi zaten kayıtlı: " + request.getEmail());
        }

        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role(request.getRole())
            .status(UserStatus.PENDING_VERIFICATION)
            .emailVerificationToken(UUID.randomUUID().toString())
            .verificationExpiresAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).plusHours(24))
            .build();

        user = userRepository.saveAndFlush(user);
        log.info("Yeni kullanıcı kaydedildi: {}", user.getEmail());

        events.enqueue(
            new com.lms.userservice.event.PlatformNotice(
                UUID.randomUUID(),
                user.getId(),
                null,
                "İleri Akademi'ye hoş geldiniz",
                "Hesabınızı kullanmaya devam etmek için e-posta adresinizi doğrulayın.",
                "/verify-email",
                "SECURITY",
                user.getEmailVerificationToken()
            )
        );

        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse login(LoginRequest request) {
        request.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        throttle.check(request.getEmail());
        User user = userRepository.findForLogin(request.getEmail()).orElseThrow(() -> {
            throttle.failure(request.getEmail());
            return new BadCredentialsException("Geçersiz e-posta veya şifre");
        });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throttle.failure(request.getEmail());
            throw new BadCredentialsException("Geçersiz e-posta veya şifre");
        }

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.INACTIVE) {
            throw new BadCredentialsException("Hesabınız aktif değil");
        }

        mfa.verify(user.getId(), request.getOtp(), false);
        throttle.success(request.getEmail());
        log.info("Kullanıcı giriş yaptı: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return response(user, sessions.create(user.getId()));
    }

    public AuthResponse refresh(String token) {
        var issued = sessions.rotate(token);
        var user = userRepository
            .findById(issued.userId())
            .orElseThrow(() -> new BadCredentialsException("Hesap bulunamadı"));
        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.INACTIVE) {
            sessions.revokeAll(user.getId());
            throw new BadCredentialsException("Hesabınız aktif değil");
        }
        return response(user, issued);
    }

    private AuthResponse response(User user, SessionService.Issued issued) {
        String accessToken = jwtTokenProvider.generateSessionAccessToken(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getFirstName() + " " + user.getLastName(),
            issued.id()
        );
        String refreshToken = issued.refreshToken();

        UserResponse userResponse = userMapper.toResponse(user);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
            .user(userResponse)
            .build();
    }
}
