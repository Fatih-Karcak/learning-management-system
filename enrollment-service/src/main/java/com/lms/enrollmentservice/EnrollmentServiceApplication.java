package com.lms.enrollmentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class EnrollmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnrollmentServiceApplication.class, args);
    }

}
