package com.lms.contentservice.service;

import com.lms.contentservice.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
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

    @Value("${minio.bucket.raw}")
    private String rawBucket;

    @Value("${minio.bucket.hls}")
    private String hlsBucket;

    @Value("${minio.bucket.images}")
    private String imagesBucket;

    @PostConstruct
    public void initBuckets() {
        ensureBucket(rawBucket);
        ensureBucket(hlsBucket);
        ensureBucket(imagesBucket);
    }

    public void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket created: {}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("Bucket check or creation failed: " + e.getMessage());
        }
    }

    public void upload(String bucket, String key, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new StorageException("The file could not be uploaded: " + e.getMessage());
        }
    }

    public void uploadLocalFile(String bucket, String key, Path localPath, String contentType) {
        try {
            minioClient.uploadObject(
                UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .filename(localPath.toString())
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception e) {
            throw new StorageException("The file could not be uploaded: " + e.getMessage());
        }
    }

    public InputStream download(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Could not download the file: " + e.getMessage());
        }
    }

    public String presignedGetUrl(String bucket, String key, int expirySeconds) {
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
            throw new StorageException("The URL could not be generated: " + e.getMessage());
        }
    }

    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Could not delete the file: " + e.getMessage());
        }
    }

    public void compose(String bucket, String key, java.util.List<String> parts) {
        try {
            minioClient.composeObject(ComposeObjectArgs.builder().bucket(bucket).object(key)
                .sources(parts.stream().map(part -> ComposeSource.builder().bucket(bucket).object(part).build()).toList())
                .headers(java.util.Map.of("Content-Type", "application/octet-stream")).build());
        } catch (Exception e) { throw new StorageException("Could not assemble the uploaded video"); }
    }

    public void deletePrefix(String bucket, String prefix) {
        try {
            var objects = new java.util.ArrayList<io.minio.messages.DeleteObject>();
            for (var result : minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build()
            )) {
                objects.add(new io.minio.messages.DeleteObject(result.get().objectName()));
            }
            if (objects.isEmpty()) return;
            for (var result : minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucket).objects(objects).build()
            )) {
                throw new StorageException("The HLS file could not be deleted: " + result.get().message());
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("The HLS files could not be deleted: " + ex.getMessage());
        }
    }
}
