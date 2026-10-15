package com.lms.notificationservice.config;

import com.lms.notificationservice.event.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private <T> ConsumerFactory<String, T> factoryFor(Class<T> type) {
        JacksonJsonDeserializer<T> deserializer = new JacksonJsonDeserializer<>(type, false);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "notification-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class
        ), new StringDeserializer(), deserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactoryFor(Class<T> type) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(factoryFor(type));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> userRegisteredContainerFactory() {
        return containerFactoryFor(UserRegisteredEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CoursePublishedEvent> coursePublishedContainerFactory() {
        return containerFactoryFor(CoursePublishedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EnrollmentCreatedEvent> enrollmentCreatedContainerFactory() {
        return containerFactoryFor(EnrollmentCreatedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentReceiptEvent> paymentReceiptContainerFactory() {
        return containerFactoryFor(PaymentReceiptEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CertificateIssuedEvent> certificateIssuedContainerFactory() {
        return containerFactoryFor(CertificateIssuedEvent.class);
    }
}