package com.lms.userservice.service;

import com.lms.userservice.domain.enums.*;
import com.lms.userservice.dto.response.UserResponse;
import com.lms.userservice.exception.UserNotFoundException;
import com.lms.userservice.mapper.UserMapper;
import com.lms.userservice.repository.UserRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final UserMapper mapper;
    private final JdbcTemplate jdbc;

    public record Update(
        @NotNull UserRole role,
        @NotNull UserStatus status,
        @NotBlank @Size(min = 3, max = 1000) String reason
    ) {}

    public Map<String, Object> summary() {
        return jdbc.queryForMap(
            "select count(*) as total, count(*) filter(where role='STUDENT') as students, " +
                "count(*) filter(where role='INSTRUCTOR') as instructors, count(*) filter(where role='ADMIN') as admins, " +
                "count(*) filter(where status='BANNED') as banned from users"
        );
    }

    @Transactional
    public UserResponse update(UUID id, Update request, UUID actor) {
        // Serialize admin changes, then recheck the actor after waiting for the locks.
        users.lockActiveAdmins();
        var acting = users
            .findById(actor)
            .orElseThrow(() -> new UserNotFoundException("Yönetici bulunamadı"));
        if (acting.getRole() != UserRole.ADMIN || acting.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yönetici hesabı aktif değil");
        }
        if (id.equals(actor)) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Kendi yönetici yetkinizi veya durumunuzu değiştiremezsiniz"
        );
        var user = users.findById(id).orElseThrow(() -> new UserNotFoundException("Kullanıcı bulunamadı"));
        user.setRole(request.role());
        user.setStatus(request.status());
        users.save(user);
        jdbc.update(
            "insert into admin_audit(id,actor_id,target_id,action,reason) values (?,?,?,?,?)",
            UUID.randomUUID(),
            actor,
            id,
            "USER_" + request.role() + "_" + request.status(),
            request.reason().trim()
        );
        return mapper.toResponse(user);
    }

    public List<Map<String, Object>> audit(int page) {
        return jdbc.queryForList(
            "select * from admin_audit order by created_at desc limit 25 offset ?",
            Math.max(0, page) * 25L
        );
    }
}
