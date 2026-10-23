package com.lms.contentservice.config;

import com.lms.contentservice.client.EnrollmentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Value("${services.enrollment.base-url}")
    private String enrollmentServiceUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Bean
    public EnrollmentClient enrollmentClient(WebClient.Builder builder) {
        WebClient webClient = builder.baseUrl(enrollmentServiceUrl)
                .defaultHeader("X-Internal-Service-Key", internalApiKey).build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();

        return factory.createClient(EnrollmentClient.class);
    }
}
