package com.lms.contentservice.exception;

import com.lms.contentservice.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LmsException.class)
    public ResponseEntity<ApiResponse<Void>> handleLmsException(LmsException ex) {
        HttpStatus status = switch (ex) {
            case MediaNotFoundException e            -> HttpStatus.NOT_FOUND;
            case UnsupportedMediaException e          -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case StorageException e                   -> HttpStatus.BAD_GATEWAY;
            case UnauthorizedMediaAccessException e   -> HttpStatus.FORBIDDEN;
            default -> throw new IllegalStateException("Unexpected value: " + ex);
        };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Dosya boyutu izin verilen sınırı aşıyor"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Sunucu hatası oluştu"));
    }
}