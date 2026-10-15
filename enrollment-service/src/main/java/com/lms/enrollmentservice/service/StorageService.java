package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.certificates}")
    private String certificatesBucket;

    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(certificatesBucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(certificatesBucket).build());
            }
        } catch (Exception e) {
            throw new StorageException("Bucket oluşturulamadı: " + e.getMessage());
        }
    }

    public void upload(String key, byte[] data, String contentType) {
        try (var in = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(certificatesBucket).object(key)
                    .stream(in, data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("PDF yüklenemedi: " + e.getMessage());
        }
    }

    public String presignedGetUrl(String key, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET).bucket(certificatesBucket).object(key)
                            .expiry(expirySeconds).build());
        } catch (Exception e) {
            throw new StorageException("URL üretilemedi: " + e.getMessage());
        }
    }
}