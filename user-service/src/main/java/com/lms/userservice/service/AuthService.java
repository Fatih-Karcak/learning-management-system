package com.lms.userservice.service;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.UserStatus;
import com.lms.userservice.dto.request.LoginRequest;
import com.lms.userservice.dto.request.RegisterRequest;
import com.lms.userservice.dto.response.AuthResponse;
import com.lms.userservice.dto.response.UserResponse;
import com.lms.userservice.exception.EmailAlreadyExistsException;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import com.lms.userservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Bu e-posta adresi zaten kayıtlı: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerificationToken(UUID.randomUUID().toString())
                .build();

        user = userRepository.save(user);
        log.info("Yeni kullanıcı kaydedildi: {}", user.getEmail());

        // Kafka'ya kayıt eventi gönder (Notification Service dinleyecek)
        kafkaTemplate.send("user-registered", user.getId().toString(),
                new UserRegisteredEvent(user.getId(), user.getEmail(),
                        user.getFirstName(), user.getEmailVerificationToken()));

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Geçersiz e-posta veya şifre"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Geçersiz e-posta veya şifre");
        }

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BadCredentialsException("Hesabınız askıya alınmış");
        }

        log.info("Kullanıcı giriş yaptı: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(),user.getFirstName() + " " + user.getLastName());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .user(userResponse)
                .build();
    }

    // Inner event class
    public record UserRegisteredEvent(
            UUID userId, String email, String firstName, String verificationToken) {}
}