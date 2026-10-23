package com.lms.courseservice.config;

import com.lms.courseservice.client.EnrollmentClient;
import com.lms.courseservice.client.ContentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Configuration
public class HttpClientConfig {
    @Bean
    public ContentClient contentClient(
            @Value("${services.content.base-url:http://localhost:8085}") String url,
            @Value("${internal.api-key:}") String key) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient client = RestClient.builder().baseUrl(url).requestFactory(requestFactory)
                .defaultHeader("X-Internal-Service-Key", key).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(ContentClient.class);
    }

    @Bean
    public EnrollmentClient enrollmentClient(
            @Value("${services.enrollment.base-url:http://localhost:8083}") String url,
            @Value("${internal.api-key:}") String key) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient client = RestClient.builder().baseUrl(url).requestFactory(requestFactory)
                .defaultHeader("X-Internal-Service-Key", key).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(EnrollmentClient.class);
    }
}
