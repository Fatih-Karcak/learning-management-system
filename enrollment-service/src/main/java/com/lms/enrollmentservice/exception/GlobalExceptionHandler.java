package com.lms.enrollmentservice.exception;

import com.lms.enrollmentservice.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LmsException.class)
    public ResponseEntity<ApiResponse<Void>> handleLms(
            LmsException ex) {

        HttpStatus status = switch (ex) {
            case EnrollmentNotFoundException ignored ->
                    HttpStatus.NOT_FOUND;
            case StorageException ignored ->
                    HttpStatus.BAD_GATEWAY;
            default -> throw new IllegalStateException("Unexpected value: " + ex);
        };

        return ResponseEntity.status(status)
                .body(
                        ApiResponse.error(ex.getMessage())
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex) {

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(ex.getMessage())
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "İstek alanları geçersiz"
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex) {

        log.error("Beklenmeyen sunucu hatası", ex);

        return ResponseEntity.status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        ApiResponse.error(
                                "Sunucu hatası oluştu"
                        )
                );
    }
}