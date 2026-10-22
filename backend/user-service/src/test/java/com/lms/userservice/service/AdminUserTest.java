package com.lms.userservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.*;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminUserTest {

    final UserRepository users = mock(UserRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final UserMapper mapper = mock(UserMapper.class);
    final AdminUserService service = new AdminUserService(users, mapper, jdbc);
    final UUID actor = UUID.randomUUID(),
        target = UUID.randomUUID();

    @Test
    void cannotRemoveOwnAdminOrActAfterLosingAdminStatus() {
        var admin = User.builder().id(actor).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(actor)).thenReturn(Optional.of(admin));
        assertThrows(ResponseStatusException.class, () ->
            service.update(
                actor,
                new AdminUserService.Update(UserRole.STUDENT, UserStatus.ACTIVE, "reason"),
                actor
            )
        );
        admin.setStatus(UserStatus.BANNED);
        assertThrows(ResponseStatusException.class, () ->
            service.update(
                target,
                new AdminUserService.Update(UserRole.STUDENT, UserStatus.ACTIVE, "reason"),
                actor
            )
        );
        verify(users, never()).save(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void emailVerificationCannotReactivateBannedOrInactiveUser() {
        for (var status : List.of(UserStatus.BANNED, UserStatus.INACTIVE)) {
            var user = User.builder().id(target).status(status).emailVerificationToken("token").build();
            when(users.findByEmailVerificationToken("token")).thenReturn(Optional.of(user));
            new UserService(users, mock(PasswordEncoder.class), mapper).verifyEmail("token");
            assertEquals(status, user.getStatus());
            assertTrue(user.isEmailVerified());
        }
    }
}
