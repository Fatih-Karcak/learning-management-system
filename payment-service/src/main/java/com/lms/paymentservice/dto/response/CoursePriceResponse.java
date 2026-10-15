// payment-service/dto/response/CoursePriceResponse.java
package com.lms.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)   // ← CourseResponse'un fazladan alanları (description, sections vb.) artık patlatmıyor
public record CoursePriceResponse(
        UUID id, String title, String thumbnailUrl, String instructorName,
        BigDecimal price, BigDecimal discountPrice
) {
    public BigDecimal effectivePrice() {
        return discountPrice != null ? discountPrice : price;
    }
}