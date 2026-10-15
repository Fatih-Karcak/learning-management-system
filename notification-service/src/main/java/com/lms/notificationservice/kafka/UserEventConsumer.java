package com.lms.notificationservice.kafka;
import com.lms.notificationservice.event.UserRegisteredEvent;
import com.lms.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {
    private final NotificationService notificationService;
    @KafkaListener(topics = "user-registered",groupId = "notification-service",containerFactory = "userRegisteredContainerFactory")
    public void handleUserRegistered(UserRegisteredEvent event){
        log.info("kullanıcı kaydı eventi alındı: {}",event.userId());
        notificationService.sendWelcomeEmail(event);
    }




}
