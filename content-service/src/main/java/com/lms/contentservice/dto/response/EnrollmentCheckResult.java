package com.lms.contentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnrollmentCheckResult(boolean success, String message, Boolean data) {}