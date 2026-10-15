package com.lms.notificationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfoResponse(UUID id, String email, String firstName, String lastName) {
}
