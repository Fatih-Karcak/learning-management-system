package com.lms.notificationservice.dto.response;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown=true)
public record UserInfoResult(boolean success, String message, UserInfoResponse data) {
}
