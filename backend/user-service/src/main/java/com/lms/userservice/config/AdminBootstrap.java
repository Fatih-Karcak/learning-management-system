package com.lms.userservice.config;

import com.lms.userservice.domain.entity.User;
import com.lms.userservice.domain.enums.*;
import com.lms.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
        UserRepository users,
        PasswordEncoder encoder,
        @Value("${APP_BOOTSTRAP_ADMIN_EMAIL:}") String email,
        @Value("${APP_BOOTSTRAP_ADMIN_PASSWORD:}") String password
    ) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) return;
        if (!email.contains("@") || password.length() < 12) throw new IllegalStateException(
            "İlk yönetici için geçerli e-posta ve en az 12 karakter parola gerekli"
        );
        var existing = users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) throw new IllegalStateException(
                "İlk yönetici e-postası başka bir hesap tarafından kullanılıyor"
            );
            return;
        }
        users.save(
            User.builder()
                .email(email.trim().toLowerCase(java.util.Locale.ROOT))
                .password(encoder.encode(password))
                .firstName("Platform")
                .lastName("Yöneticisi")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build()
        );
    }
}
