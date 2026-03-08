package com.rumal.vendor_service.service;

import com.rumal.vendor_service.config.ObjectStorageProperties;
import com.rumal.vendor_service.dto.VendorMediaAssetType;
import com.rumal.vendor_service.dto.VendorMediaPrepareUploadItem;
import com.rumal.vendor_service.dto.VendorMediaPrepareUploadRequest;
import com.rumal.vendor_service.dto.VendorMediaPrepareUploadResponse;
import com.rumal.vendor_service.dto.VendorMediaPresignedUpload;
import com.rumal.vendor_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VendorMediaStorageServiceImpl implements VendorMediaStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern KEY_PATTERN = Pattern.compile("^vendors/[0-9a-f-]+/(logo|banner)/[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

    private final ObjectStorageProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    @Value("${vendor.media.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes;

    @Override
    public VendorMediaPrepareUploadResponse prepareUploads(UUID vendorId, VendorMediaPrepareUploadRequest request) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new ValidationException("Object storage presigner is not configured");
        }
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new ValidationException("At least one media file is required");
        }
        if (request.files().size() > 2) {
            throw new ValidationException("At most two vendor media uploads can be prepared at once");
        }

        Instant expiresAt = Instant.now().plus(properties.presignExpiry());
        List<VendorMediaPresignedUpload> uploads = new ArrayList<>();
        for (VendorMediaPrepareUploadItem file : request.files()) {
            String extension = extractExtension(file.fileName());
            String contentType = normalizeRequestedContentType(file.contentType(), extension);
            if (file.sizeBytes() > maxFileSizeBytes) {
                throw new ValidationException("Vendor media exceeds the maximum allowed size");
            }
            String assetSegment = assetSegment(file.assetType());
            String key = "vendors/" + vendorId + "/" + assetSegment + "/" + UUID.randomUUID() + "." + extension;
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .cacheControl("public, max-age=31536000, immutable")
                    .contentType(contentType)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(properties.presignExpiry())
                    .putObjectRequest(putObjectRequest)
                    .build();
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            uploads.add(new VendorMediaPresignedUpload(
                    file.assetType(),
                    key,
                    presignedRequest.url().toString(),
                    contentType,
                    expiresAt
            ));
        }
        return new VendorMediaPrepareUploadResponse(List.copyOf(uploads));
    }

    @Override
    public String assertVendorMediaReady(UUID vendorId, VendorMediaAssetType assetType, String key) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new ValidationException("Object storage client is not configured");
        }

        String normalizedKey = normalizeKey(key);
        String requiredPrefix = "vendors/" + vendorId + "/" + assetSegment(assetType) + "/";
        if (!normalizedKey.startsWith(requiredPrefix)) {
            throw new ValidationException("Vendor media key does not belong to the authenticated vendor");
        }

        try {
            var head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(normalizedKey)
                    .build());
            if (head.contentLength() > maxFileSizeBytes) {
                deleteQuietly(s3Client, normalizedKey);
                throw new ValidationException("Vendor media exceeds the maximum allowed size");
            }
            String contentType = head.contentType() == null ? "" : head.contentType().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                deleteQuietly(s3Client, normalizedKey);
                throw new ValidationException("Vendor media content type is not allowed");
            }

            byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(normalizedKey)
                    .build()).asByteArray();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                deleteQuietly(s3Client, normalizedKey);
                throw new ValidationException("Vendor media payload is not a valid image");
            }
            return normalizedKey;
        } catch (ValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ValidationException("Vendor media is not available in object storage", ex);
        } catch (Exception ex) {
            throw new ValidationException("Vendor media payload is not a valid image", ex);
        }
    }

    @Override
    public StoredImage getImage(String key) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new ValidationException("Object storage client is not configured");
        }
        String normalizedKey = normalizeKey(key);
        try {
            var bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(normalizedKey)
                    .build());
            String contentType = bytes.response().contentType();
            return new StoredImage(bytes.asByteArray(), contentType == null ? "image/jpeg" : contentType);
        } catch (RuntimeException ex) {
            throw new ValidationException("Vendor media not found", ex);
        }
    }

    private void deleteQuietly(S3Client s3Client, String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (RuntimeException ignored) {
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("Media key is required");
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Invalid vendor media key format");
        }
        return normalized;
    }

    private String assetSegment(VendorMediaAssetType assetType) {
        return switch (assetType) {
            case LOGO -> "logo";
            case BANNER -> "banner";
        };
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ValidationException("Media filename is required");
        }
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0 || idx == fileName.length() - 1) {
            throw new ValidationException("Media filename must include an extension");
        }
        String ext = fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Allowed vendor media extensions: jpg, jpeg, png, webp");
        }
        return ext;
    }

    private String normalizeRequestedContentType(String contentType, String extension) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        Map<String, String> expectedByExtension = new LinkedHashMap<>();
        expectedByExtension.put("jpg", "image/jpeg");
        expectedByExtension.put("jpeg", "image/jpeg");
        expectedByExtension.put("png", "image/png");
        expectedByExtension.put("webp", "image/webp");
        String expected = expectedByExtension.get(extension);
        if (expected == null) {
            throw new ValidationException("Unsupported vendor media extension");
        }
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!expected.equals(normalized)) {
            throw new ValidationException("Vendor media content type does not match the file extension");
        }
        return expected;
    }
}
