package com.lms.notificationservice.config;

import com.lms.notificationservice.client.UserClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {
    @Value("${services.user.base-url}")
    private String userServiceUrl;

    @Bean
    public UserClient userClient(WebClient.Builder builder) {
        WebClient webClient = builder.baseUrl(userServiceUrl).build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();
//
        return factory.createClient(UserClient.class);
    }

}
