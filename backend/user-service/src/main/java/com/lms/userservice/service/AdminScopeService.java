package com.lms.userservice.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminScopeService {

    private final JdbcTemplate jdbc;

    public String scope(UUID id) {
        return jdbc.queryForObject("select admin_scope from users where id=?", String.class, id);
    }

    public static boolean allows(String scope, String path, String method) {
        if ("FULL".equals(scope)) return true;
        if (
            path.startsWith("/api/v1/auth/") ||
            path.equals("/api/v1/users/me") ||
            path.startsWith("/api/v1/users/me/")
        ) return true;
        if ("MODERATION".equals(scope)) return (
            path.startsWith("/api/v1/courses/") ||
            path.startsWith("/api/v1/reviews/") ||
            ("GET".equals(method) && path.startsWith("/api/v1/media/"))
        );
        if ("SUPPORT".equals(scope)) return (
            path.startsWith("/api/v1/users/admin/tickets") ||
            path.startsWith("/api/v1/users/admin/privacy") ||
            path.startsWith("/api/v1/notifications/") ||
            path.startsWith("/api/v1/enrollments/admin/") ||
            ("GET".equals(method) &&
                ((path.startsWith("/api/v1/courses/") &&
                    !path.startsWith("/api/v1/courses/admin") &&
                    !path.contains("/workspace") &&
                    !path.endsWith("/quiz/manage") &&
                    !path.contains("/assignment/submissions")) ||
                    path.startsWith("/api/v1/enrollments/courses/")))
        );
        return false;
    }

    @Transactional
    public void set(UUID target, String scope, String reason, UUID actor) {
        jdbc.queryForList(
            "select id from users where role='ADMIN' and status='ACTIVE' order by id for update"
        );
        if (!"FULL".equals(scope(actor))) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Tam yönetici yetkisi gerekli"
        );
        if (
            target.equals(actor) || !Set.of("FULL", "MODERATION", "SUPPORT").contains(scope)
        ) throw new IllegalArgumentException("Kendi yetkinizi değiştiremezsiniz veya kapsam geçersiz");
        if (
            jdbc.update("update users set admin_scope=? where id=? and role='ADMIN'", scope, target) != 1
        ) throw new IllegalArgumentException("Hedef kullanıcı yönetici olmalı");
        jdbc.update("update user_sessions set revoked=true where user_id=?", target);
        jdbc.update(
            "insert into admin_audit(id,actor_id,target_id,action,reason) values(?,?,?, ?,?)",
            UUID.randomUUID(),
            actor,
            target,
            "ADMIN_SCOPE_" + scope,
            reason
        );
    }
}
