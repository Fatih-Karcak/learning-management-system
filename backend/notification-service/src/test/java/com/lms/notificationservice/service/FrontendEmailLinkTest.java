package com.lms.notificationservice.service;

import com.lms.notificationservice.client.UserClient;
import com.lms.notificationservice.event.UserRegisteredEvent;
import com.lms.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FrontendEmailLinkTest {
    @Test void frontendVerificationLinkUsesTheUiRouteAndEncodesTheToken() {
        assertLink("https://learn.example/", "a b", "https://learn.example/verify-email?token=a%20b");
    }

    @Test void existingApiVerificationLinkIsPreservedWithoutAFrontend() {
        assertLink("", "local-token", "https://gateway.example/api/v1/auth/verify-email?token=local-token");
    }

    private void assertLink(String frontend, String token, String expected) {
        var email = mock(EmailService.class);
        var service = new NotificationService(mock(UserClient.class), email, mock(NotificationLogRepository.class));
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://gateway.example");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", frontend);
        service.sendWelcomeEmail(new UserRegisteredEvent(UUID.randomUUID(), "local@example.test", "Test", token));
        var context = ArgumentCaptor.forClass(Context.class);
        verify(email).sendHtmlEmail(eq("local@example.test"), anyString(), eq("welcome-email"), context.capture());
        assertEquals(expected, context.getValue().getVariable("verificationUrl"));
    }
}
