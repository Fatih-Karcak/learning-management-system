package com.lms.enrollmentservice.exception;

import com.lms.enrollmentservice.dto.response.ApiResponse;
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
        // GlobalExceptionHandler.java — switch güncellendi
        HttpStatus status = switch (ex) {
            case EnrollmentNotFoundException e -> HttpStatus.NOT_FOUND;
            case StorageException e            -> HttpStatus.BAD_GATEWAY;
            default -> throw new IllegalStateException("Unexpected value: " + ex);
        };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Sunucu hatası oluştu"));
    }
}