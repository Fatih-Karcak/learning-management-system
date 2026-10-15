package com.lms.enrollmentservice.repository;

import com.lms.enrollmentservice.domain.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByVerificationCode(String code);

    Optional<Certificate> findByEnrollmentId(UUID enrollmentId);

    boolean existsByEnrollmentId(UUID enrollmentId);

}
