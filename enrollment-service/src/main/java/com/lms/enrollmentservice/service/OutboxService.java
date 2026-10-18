package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.domain.entity.OutboxEvent;
import com.lms.enrollmentservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void enqueue(
            String topic,
            String key,
            Object payload) {

        String json = objectMapper
                .writeValueAsString(payload);

        repository.save(
                new OutboxEvent(
                        topic,
                        key,
                        json
                )
        );
    }
}