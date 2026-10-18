package com.lms.paymentservice.service;

import com.lms.paymentservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(
            fixedDelayString =
                    "${payment.outbox-interval-ms:1000}"
    )
    @Transactional
    public void publishBatch() {

        var events = repository.findUnsent(
                PageRequest.of(0, 30)
        );

        for (var event : events) {

            try {
                var payload = objectMapper.readTree(
                        event.getPayload()
                );

                kafkaTemplate.send(
                        event.getTopic(),
                        event.getEventKey(),
                        payload
                ).get(10, TimeUnit.SECONDS);

                event.markSent();

            } catch (Exception e) {

                log.error(
                        "Outbox gönderilemedi. ID: {}",
                        event.getId(),
                        e
                );

                break;
            }
        }
    }
}