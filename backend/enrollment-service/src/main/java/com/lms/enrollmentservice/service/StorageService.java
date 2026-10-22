package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.exception.StorageException;

import io.minio.*;
import io.minio.http.Method;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.certificates}")
    private String bucket;

    public void upload(
            String key,
            byte[] data,
            String contentType) {

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException(
                    "Boş dosya yüklenemez"
            );
        }

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(data)) {

            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(
                                    input,
                                    data.length,
                                    -1
                            )
                            .contentType(contentType)
                            .build()
            );

        } catch (Exception e) {

            throw new StorageException(
                    "MinIO dosya yükleme başarısız: "
                            + e.getMessage()
            );
        }
    }

    public String presignedGetUrl(
            String key,
            int expirySeconds) {

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(key)
                            .expiry(expirySeconds)
                            .build()
            );

        } catch (Exception e) {

            throw new StorageException(
                    "MinIO indirme bağlantısı oluşturulamadı"
            );
        }
    }
}