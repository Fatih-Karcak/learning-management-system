package com.lms.contentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "videoProcessingExecutor")
    public Executor videoProcessingExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("video-proc-", 0).factory());
    }
}