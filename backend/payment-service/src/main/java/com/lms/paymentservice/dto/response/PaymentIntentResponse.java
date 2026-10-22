package com.lms.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PaymentIntentResponse {
    private UUID paymentId;         // bizim DB'mizdeki ID
    private String clientSecret;    // Stripe'a gönderilecek
    private BigDecimal amount;
    private String currency;
    private String courseTitle;
}