package com.lms.enrollmentservice.config;

import com.lms.enrollmentservice.security.InternalServiceAuthFilter;
import com.lms.enrollmentservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final InternalServiceAuthFilter internalFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(errors ->
                errors
                    .authenticationEntryPoint((request, response, ex) -> response.sendError(401))
                    .accessDeniedHandler((request, response, ex) -> response.sendError(403))
            )

            .authorizeHttpRequests(auth ->
                auth

                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()

                    .requestMatchers("/api/v1/enrollments/certificates/verify/**")
                    .permitAll()

                    .requestMatchers("/api/v1/internal/**")
                    .hasRole("SERVICE")

                    .anyRequest()
                    .authenticated()
            )

            .addFilterBefore(internalFilter, UsernamePasswordAuthenticationFilter.class)

            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService disabledPasswordLogin() {
        return username -> {
            throw new UsernameNotFoundException("Password authentication disabled");
        };
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        var registration = new FilterRegistrationBean<>(jwtFilter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalServiceAuthFilter> internalFilterRegistration() {
        var registration = new FilterRegistrationBean<>(internalFilter);

        registration.setEnabled(false);
        return registration;
    }
}
