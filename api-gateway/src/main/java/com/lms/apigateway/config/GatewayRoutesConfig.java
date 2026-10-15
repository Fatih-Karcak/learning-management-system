package com.lms.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayRoutesConfig {

    @Value("${services.user.base-url}")         private String userServiceUrl;
    @Value("${services.course.base-url}")        private String courseServiceUrl;
    @Value("${services.enrollment.base-url}")    private String enrollmentServiceUrl;
    @Value("${services.payment.base-url}")       private String paymentServiceUrl;
    @Value("${services.content.base-url}")       private String contentServiceUrl;
    @Value("${services.notification.base-url}")  private String notificationServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return route("user-service")
                .route(path("/api/v1/auth/**").or(path("/api/v1/users/**")), http())
                .before(uri(userServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> courseServiceRoutes() {
        return route("course-service")
                .route(path("/api/v1/courses/**")
                        .or(path("/api/v1/sections/**"))
                        .or(path("/api/v1/lessons/**"))
                        .or(path("/api/v1/reviews/**")), http())
                .before(uri(courseServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> enrollmentServiceRoutes() {
        return route("enrollment-service")
                .route(path("/api/v1/enrollments/**").or(path("/api/v1/progress/**")), http())
                .before(uri(enrollmentServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes() {
        return route("payment-service")
                .route(path("/api/v1/payments/**").or(path("/api/v1/webhooks/**")), http())
                .before(uri(paymentServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> contentServiceRoutes() {
        return route("content-service")
                .route(path("/api/v1/media/**"), http())
                .before(uri(contentServiceUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> notificationServiceRoutes() {
        return route("notification-service")
                .route(path("/api/v1/notifications/**"), http())
                .before(uri(notificationServiceUrl))
                .build();
    }
}