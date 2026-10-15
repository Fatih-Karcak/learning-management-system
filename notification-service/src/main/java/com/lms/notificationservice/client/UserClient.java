package com.lms.notificationservice.client;
import com.lms.notificationservice.dto.response.UserInfoResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange(url = "/api/v1/internal/users")
public interface UserClient {
    @GetExchange("/{id}")
    UserInfoResult getUserById(@PathVariable UUID id);
}
