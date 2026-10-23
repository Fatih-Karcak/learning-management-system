package com.lms.notificationservice.client;

import com.lms.notificationservice.dto.response.UserInfoResult;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/api/v1/internal/users")
public interface UserClient {
    record Preferences(boolean learningEmails, boolean announcements, int weeklyTargetMinutes) {}

    record PreferencesResult(boolean success, Preferences data) {}

    @GetExchange("/{id}/preferences")
    PreferencesResult preferences(@PathVariable UUID id);

    @GetExchange("/{id}")
    UserInfoResult getUserById(@PathVariable UUID id);
}
