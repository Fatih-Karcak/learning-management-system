package com.lms.userservice.controller;

import com.lms.userservice.dto.response.ApiResponse;
import com.lms.userservice.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AccountToolsController {

    private final AccountToolsService tools;
    private final SessionService sessions;
    private final MfaService mfa;
    private final UserService users;

    private UUID id(Authentication a) {
        return UUID.fromString(a.getName());
    }

    private boolean admin(Authentication a) {
        return a
            .getAuthorities()
            .stream()
            .anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
    }

    public record Otp(@Pattern(regexp = "[0-9]{6}") @NotBlank String code) {}

    public record Password(@NotBlank String password) {}

    public record Reason(@NotBlank @Size(max = 2000) String reason) {}

    @GetMapping("/me/preferences")
    public ApiResponse<?> preferences(Authentication a) {
        return ApiResponse.success(tools.preferences(id(a)), "Tercihler");
    }

    @PutMapping("/me/preferences")
    public ApiResponse<?> preferences(
        Authentication a,
        @Valid @RequestBody AccountToolsService.Preferences p
    ) {
        return ApiResponse.success(tools.savePreferences(id(a), p), "Kaydedildi");
    }

    @GetMapping("/me/sessions")
    public ApiResponse<?> sessions(Authentication a) {
        return ApiResponse.success(sessions.list(id(a)), "Oturumlar");
    }

    @DeleteMapping("/me/sessions/{session}")
    public ApiResponse<?> revoke(Authentication a, @PathVariable UUID session) {
        sessions.revoke(id(a), session);
        return ApiResponse.success(null, "Oturum kapatıldı");
    }

    @DeleteMapping("/me/sessions")
    public ApiResponse<?> revokeAll(Authentication a) {
        sessions.revokeAll(id(a));
        return ApiResponse.success(null, "Oturumlar kapatıldı");
    }

    @GetMapping("/me/mfa")
    public ApiResponse<?> mfa(Authentication a) {
        return ApiResponse.success(Map.of("enabled", mfa.enabled(id(a))), "Doğrulama");
    }

    @PostMapping("/me/mfa/setup")
    public ApiResponse<?> setup(Authentication a, @Valid @RequestBody Password p) {
        toolsCheckPassword(a, p.password());
        return ApiResponse.success(mfa.setup(id(a), users.getUserById(id(a)).getEmail()), "Kurulum");
    }

    @PostMapping("/me/mfa/confirm")
    public ApiResponse<?> confirm(Authentication a, @Valid @RequestBody Otp p) {
        mfa.verify(id(a), p.code(), true);
        return ApiResponse.success(null, "Doğrulama açıldı");
    }

    @DeleteMapping("/me/mfa")
    public ApiResponse<?> disable(Authentication a, @Valid @RequestBody Otp p) {
        mfa.disable(id(a), p.code());
        sessions.revokeAll(id(a));
        return ApiResponse.success(null, "Doğrulama kapatıldı; tekrar giriş yapın");
    }

    private final com.lms.userservice.repository.UserRepository repository;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    private void toolsCheckPassword(Authentication a, String raw) {
        if (
            !encoder.matches(raw, repository.findById(id(a)).orElseThrow().getPassword())
        ) throw new IllegalArgumentException("Parola yanlış");
    }

    @GetMapping("/me/export")
    public ApiResponse<?> export(Authentication a) {
        return ApiResponse.success(tools.export(id(a)), "Kişisel veriler");
    }

    @PostMapping("/me/erasure")
    public ApiResponse<?> erase(Authentication a, @Valid @RequestBody Password p) {
        tools.requestErasure(id(a), p.password());
        return ApiResponse.success(null, "Hesap kapatıldı; silme talebi incelemeye alındı");
    }

    @GetMapping("/me/tickets")
    public ApiResponse<?> tickets(Authentication a) {
        return ApiResponse.success(tools.tickets(id(a), false), "Destek");
    }

    @PostMapping("/me/tickets")
    public ApiResponse<?> create(Authentication a, @Valid @RequestBody AccountToolsService.Ticket t) {
        return ApiResponse.success(tools.create(id(a), t), "Talep açıldı");
    }

    @GetMapping("/me/tickets/{ticket}/replies")
    public ApiResponse<?> replies(Authentication a, @PathVariable UUID ticket) {
        return ApiResponse.success(tools.replies(ticket, id(a), admin(a)), "Yanıtlar");
    }

    @PostMapping("/me/tickets/{ticket}/replies")
    public ApiResponse<?> reply(
        Authentication a,
        @PathVariable UUID ticket,
        @Valid @RequestBody AccountToolsService.Reply r
    ) {
        tools.reply(ticket, id(a), admin(a), r);
        return ApiResponse.success(null, "Yanıt kaydedildi");
    }

    @GetMapping("/admin/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> ticketsAdmin(Authentication a) {
        return ApiResponse.success(tools.tickets(id(a), true), "Destek kuyruğu");
    }

    @GetMapping("/admin/privacy")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> privacy(Authentication a) {
        return ApiResponse.success(tools.privacyRequests(id(a), true), "Veri talepleri");
    }

    @PostMapping("/admin/privacy/{request}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> privacy(
        Authentication a,
        @PathVariable UUID request,
        @Valid @RequestBody Reason r
    ) {
        tools.resolvePrivacy(request, id(a), r.reason());
        return ApiResponse.success(null, "İnceleme kaydedildi");
    }
}
