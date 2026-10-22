package com.lms.userservice.service;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.UserStatus;
import com.lms.userservice.dto.request.UpdateProfileRequest;
import com.lms.userservice.dto.response.UserResponse;
import com.lms.userservice.exception.UserNotFoundException;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findUserById(id));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = findUserById(id);

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        if (StringUtils.hasText(request.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        // Şifre güncelleme
        if (StringUtils.hasText(request.getNewPassword())) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Mevcut şifre yanlış");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        user = userRepository.save(user);
        log.info("Kullanıcı profili güncellendi: {}", id);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz doğrulama token'ı"));

        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        log.info("E-posta doğrulandı: {}", user.getEmail());
    }

    public Page<UserResponse> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable)
                .map(userMapper::toResponse);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = findUserById(id);
        if (user.getRole() == com.lms.userservice.domain.enums.UserRole.ADMIN) {
            throw new IllegalArgumentException("Yönetici hesabını başka bir yönetici devre dışı bırakmalıdır");
        }
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Kullanıcı bulunamadı: " + id));
    }
}
