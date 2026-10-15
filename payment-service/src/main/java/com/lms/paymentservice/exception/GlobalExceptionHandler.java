package com.lms.paymentservice.exception;

import com.lms.paymentservice.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LmsException.class)
    public ResponseEntity<ApiResponse<Void>> handleLmsException(LmsException ex) {
        // Java 25 — pattern matching switch
        HttpStatus status = switch (ex) {
            case PaymentNotFoundException e  -> HttpStatus.NOT_FOUND;
            case PaymentFailedException e    -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> throw new IllegalStateException("Unexpected value: " + ex);
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Sunucu hatası"));
    }
}