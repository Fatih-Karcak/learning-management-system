package com.lms.enrollmentservice.controller;

import com.lms.enrollmentservice.dto.response.ApiResponse;
import com.lms.enrollmentservice.service.OrganizationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments/organizations")
@RequiredArgsConstructor
public class OrganizationController {
  private final OrganizationService service;

  private UUID user(Authentication auth) {
    return UUID.fromString(auth.getName());
  }

  private boolean admin(Authentication auth) {
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }

  public record Reply(boolean accept) {}

  @GetMapping
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> list(Authentication auth, @RequestParam(defaultValue = "0") int page) {
    return ApiResponse.success(
        service.organizations(user(auth), admin(auth), page), "Organizations");
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> create(
      Authentication auth, @Valid @RequestBody OrganizationService.Name input) {
    return ApiResponse.success(service.create(user(auth), input), "Organization created");
  }

  @GetMapping("/invitations")
  public ApiResponse<?> invitations(Authentication auth) {
    return ApiResponse.success(service.invitations(user(auth)), "Group invitations");
  }

  @PostMapping("/groups/{group}/reply")
  public ApiResponse<?> reply(
      @PathVariable UUID group, Authentication auth, @RequestBody Reply input) {
    service.reply(group, user(auth), input.accept());
    return ApiResponse.success(null, "Invitation answered");
  }

  @GetMapping("/{org}")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> detail(@PathVariable UUID org, Authentication auth) {
    return ApiResponse.success(service.detail(org, user(auth), admin(auth)), "Organization");
  }

  @PostMapping("/{org}/groups")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> group(
      @PathVariable UUID org,
      Authentication auth,
      @Valid @RequestBody OrganizationService.Name input) {
    return ApiResponse.success(
        service.createGroup(org, user(auth), admin(auth), input), "Group created");
  }

  @GetMapping("/groups/{group}/members")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> members(
      @PathVariable UUID group, Authentication auth, @RequestParam(defaultValue = "0") int page) {
    return ApiResponse.success(
        service.members(group, user(auth), admin(auth), page), "Group members");
  }

  @PostMapping("/groups/{group}/members")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> invite(
      @PathVariable UUID group,
      Authentication auth,
      @Valid @RequestBody OrganizationService.Members input) {
    return ApiResponse.success(
        service.invite(group, user(auth), admin(auth), input), "Invitations created");
  }

  @DeleteMapping("/groups/{group}/members/{student}")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> remove(
      @PathVariable UUID group, @PathVariable UUID student, Authentication auth) {
    service.remove(group, student, user(auth), admin(auth));
    return ApiResponse.success(null, "Membership removed");
  }

  @PostMapping("/groups/{group}/courses")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> assign(
      @PathVariable UUID group,
      Authentication auth,
      @Valid @RequestBody OrganizationService.Assignment input) {
    service.assign(group, user(auth), admin(auth), input);
    return ApiResponse.success(null, "Course assigned");
  }

  @GetMapping("/groups/{group}/report")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ApiResponse<?> report(
      @PathVariable UUID group, Authentication auth, @RequestParam(defaultValue = "0") int page) {
    return ApiResponse.success(
        service.report(group, user(auth), admin(auth), page), "Training report");
  }

  @GetMapping(value = "/groups/{group}/report.csv", produces = "text/csv;charset=UTF-8")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  public ResponseEntity<String> csv(@PathVariable UUID group, Authentication auth) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=training-report.csv")
        .header("Cache-Control", "no-store")
        .body(service.csv(group, user(auth), admin(auth)));
  }
}
