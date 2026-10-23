package com.lms.enrollmentservice.service;

import com.lms.enrollmentservice.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.public-endpoint:}")
    private String publicEndpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket.certificates}")
    private String bucket;

    public void upload(String key, byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Boş dosya yüklenemez");
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());

            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, data.length, -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new StorageException("MinIO dosya yükleme başarısız: " + e.getMessage());
        }
    }

    public String presignedGetUrl(String key, int expirySeconds) {
        try {
            var signingClient =
                publicEndpoint == null || publicEndpoint.isBlank()
                    ? minioClient
                    : MinioClient.builder()
                          .endpoint(publicEndpoint)
                          .credentials(accessKey, secretKey)
                          .region("us-east-1")
                          .build();
            return signingClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expirySeconds)
                    .build()
            );
        } catch (Exception e) {
            throw new StorageException("MinIO indirme bağlantısı oluşturulamadı");
        }
    }
}
