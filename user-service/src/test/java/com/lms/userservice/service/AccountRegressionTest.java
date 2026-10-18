package com.lms.userservice.service;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.*;
import com.lms.userservice.dto.request.*;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import com.lms.userservice.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountRegressionTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UserMapper mapper = mock(UserMapper.class);

    @Test void publicRegistrationRejectsAdminBeforeWritingAnything() {
        var service = new AuthService(users, passwords, mock(JwtTokenProvider.class), mapper, mock(KafkaTemplate.class));
        var request = new RegisterRequest(); request.setRole(UserRole.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verifyNoInteractions(users, passwords);
    }

    @Test void inactiveAndBannedAccountsCannotLoginOrAuthenticateExistingToken() {
        var service = new AuthService(users, passwords, mock(JwtTokenProvider.class), mapper, mock(KafkaTemplate.class));
        var details = new CustomUserDetailsService(users);
        for (UserStatus status : new UserStatus[]{UserStatus.INACTIVE, UserStatus.BANNED}) {
            var user = User.builder().id(UUID.randomUUID()).email("test@example.test")
                    .password("hashed").role(UserRole.STUDENT).status(status).build();
            when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(users.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwords.matches("password", "hashed")).thenReturn(true);
            var login = new LoginRequest(); login.setEmail(user.getEmail()); login.setPassword("password");
            assertThrows(BadCredentialsException.class, () -> service.login(login));
            assertThrows(DisabledException.class, () -> details.loadUserByUsername(user.getId().toString()));
        }
    }

    @Test void unrelatedProfileUpdatePreservesBiographyAndExplicitEmptyStringClearsIt() {
        var user = User.builder().id(UUID.randomUUID()).bio("Existing biography").build();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        var service = new UserService(users, passwords, mapper);
        var update = new UpdateProfileRequest(); update.setFirstName("Updated");
        service.updateProfile(user.getId(), update);
        assertEquals("Existing biography", user.getBio());
        update = new UpdateProfileRequest(); update.setBio("");
        service.updateProfile(user.getId(), update);
        assertEquals("", user.getBio());
    }
}
