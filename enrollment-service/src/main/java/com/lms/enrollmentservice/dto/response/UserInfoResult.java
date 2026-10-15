package com.lms.enrollmentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfoResult(boolean success, String message, UserInfoResponse data) {}