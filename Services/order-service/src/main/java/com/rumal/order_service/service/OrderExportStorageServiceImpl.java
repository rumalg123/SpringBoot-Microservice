package com.rumal.order_service.service;

import com.rumal.order_service.config.ObjectStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderExportStorageServiceImpl implements OrderExportStorageService {

    private static final Logger log = LoggerFactory.getLogger(OrderExportStorageServiceImpl.class);

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectStorageProperties objectStorageProperties;
    private final Path localRootDirectory;

    public OrderExportStorageServiceImpl(
            ObjectProvider<S3Client> s3ClientProvider,
            ObjectStorageProperties objectStorageProperties,
            @Value("${order.export.storage.local-dir:${java.io.tmpdir}/rumal-order-exports}") String localDirectory
    ) {
        this.s3ClientProvider = s3ClientProvider;
        this.objectStorageProperties = objectStorageProperties;
        this.localRootDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
    }

    @Override
    public StoredOrderExportFile store(UUID jobId, String fileName, String contentType, byte[] content) {
        byte[] safeContent = content == null ? new byte[0] : content;
        String key = buildStorageKey(jobId, fileName);
        String resolvedContentType = normalizeContentType(contentType);
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client != null && objectStorageProperties.enabled()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(objectStorageProperties.bucket())
                            .key(key)
                            .contentType(resolvedContentType)
                            .build(),
                    RequestBody.fromBytes(safeContent)
            );
            return new StoredOrderExportFile(key, fileName, resolvedContentType, safeContent.length, safeContent);
        }

        Path targetPath = resolveLocalPath(key);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, safeContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return new StoredOrderExportFile(key, fileName, resolvedContentType, safeContent.length, safeContent);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist export file locally", ex);
        }
    }

    @Override
    public StoredOrderExportFile load(String storageKey, String fileName, String contentType) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException("Export storage key is missing");
        }
        String resolvedContentType = normalizeContentType(contentType);
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client != null && objectStorageProperties.enabled()) {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(objectStorageProperties.bucket())
                            .key(storageKey)
                            .build()
            ).asByteArray();
            return new StoredOrderExportFile(storageKey, fileName, resolvedContentType, bytes.length, bytes);
        }

        Path targetPath = resolveLocalPath(storageKey);
        try {
            byte[] bytes = Files.readAllBytes(targetPath);
            return new StoredOrderExportFile(storageKey, fileName, resolvedContentType, bytes.length, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read export file", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client != null && objectStorageProperties.enabled()) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(objectStorageProperties.bucket())
                        .key(storageKey)
                        .build());
            } catch (RuntimeException ex) {
                log.warn("Failed deleting export file from object storage, key={}", storageKey, ex);
            }
            return;
        }

        try {
            Files.deleteIfExists(resolveLocalPath(storageKey));
        } catch (IOException ex) {
            log.warn("Failed deleting local export file, key={}", storageKey, ex);
        }
    }

    private String buildStorageKey(UUID jobId, String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        return "order-exports/" + jobId + "/" + safeFileName;
    }

    private Path resolveLocalPath(String storageKey) {
        String normalizedKey = storageKey.replace('\\', '/');
        return localRootDirectory.resolve(normalizedKey).normalize();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "orders-export.csv";
        }
        String safe = fileName.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.toLowerCase(Locale.ROOT).endsWith(".csv") ? safe : safe + ".csv";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "text/csv";
        }
        return contentType.trim();
    }
}
