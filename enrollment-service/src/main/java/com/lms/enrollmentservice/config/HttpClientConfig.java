package com.lms.enrollmentservice.config;

import com.lms.enrollmentservice.client.CourseClient;
import com.lms.enrollmentservice.client.UserClient;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Value("${services.course.base-url}")
    private String courseUrl;

    @Value("${services.user.base-url}")
    private String userUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private WebClient.Builder newBuilder() {

        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        3000
                )
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(
                                httpClient
                        )
                );
    }

    @Bean
    public CourseClient courseClient() {

        WebClient client = newBuilder()
                .baseUrl(courseUrl)
                .build();

        return HttpServiceProxyFactory
                .builderFor(
                        WebClientAdapter.create(client)
                )
                .build()
                .createClient(CourseClient.class);
    }

    @Bean
    public UserClient userClient() {

        WebClient client = newBuilder()
                .baseUrl(userUrl)
                .defaultHeader(
                        "X-Internal-Service-Key",
                        internalApiKey
                )
                .build();

        return HttpServiceProxyFactory
                .builderFor(
                        WebClientAdapter.create(client)
                )
                .build()
                .createClient(UserClient.class);
    }
}