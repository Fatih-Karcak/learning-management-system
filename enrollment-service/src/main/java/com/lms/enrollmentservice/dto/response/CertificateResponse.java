package com.lms.enrollmentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CertificateResponse {
    private UUID id;
    private String verificationCode;
    private String studentName;
    private String courseTitle;
    private String instructorName;
    private LocalDateTime issuedAt;
    private String pdfUrl;
}