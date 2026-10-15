package com.lms.paymentservice.config;

import com.lms.paymentservice.client.CourseClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Value("${services.course.base-url}")
    private String courseServiceUrl;

    @Bean
    public CourseClient courseClient(WebClient.Builder builder) {
        WebClient webClient = builder
                .baseUrl(courseServiceUrl)
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();

        return factory.createClient(CourseClient.class);
    }
}