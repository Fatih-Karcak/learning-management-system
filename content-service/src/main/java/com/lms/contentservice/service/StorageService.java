package com.lms.contentservice.service;

import com.lms.contentservice.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.raw}")    private String rawBucket;
    @Value("${minio.bucket.hls}")    private String hlsBucket;
    @Value("${minio.bucket.images}") private String imagesBucket;

    @PostConstruct
    public void initBuckets() {
        ensureBucket(rawBucket);
        ensureBucket(hlsBucket);
        ensureBucket(imagesBucket);
    }

    public void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket oluşturuldu: {}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("Bucket kontrol/oluşturma hatası: " + e.getMessage());
        }
    }

    public void upload(String bucket, String key, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Dosya yüklenemedi: " + e.getMessage());
        }
    }

    public void uploadLocalFile(String bucket, String key, Path localPath, String contentType) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .filename(localPath.toString())
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Dosya yüklenemedi: " + e.getMessage());
        }
    }

    public InputStream download(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Dosya indirilemedi: " + e.getMessage());
        }
    }

    public String presignedGetUrl(String bucket, String key, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket).object(key)
                            .expiry(expirySeconds)
                            .build());
        } catch (Exception e) {
            throw new StorageException("URL üretilemedi: " + e.getMessage());
        }
    }

    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Dosya silinemedi (yok sayılıyor): {}/{} - {}", bucket, key, e.getMessage());
        }
    }
}