package com.lms.enrollmentservice.config;

import com.lms.enrollmentservice.client.CourseClient;
import com.lms.enrollmentservice.client.UserClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Value("${services.course.base-url}") private String courseServiceUrl;
    @Value("${services.user.base-url}")   private String userServiceUrl;

    @Bean
    public CourseClient courseClient(WebClient.Builder builder) {
        return HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(builder.baseUrl(courseServiceUrl).build()))
                .build()
                .createClient(CourseClient.class);
    }

    @Bean
    public UserClient userClient(WebClient.Builder builder) {
        return HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(builder.baseUrl(userServiceUrl).build()))
                .build()
                .createClient(UserClient.class);
    }
}