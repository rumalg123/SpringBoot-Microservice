package com.rumal.poster_service.storage;

import com.rumal.poster_service.config.ObjectStorageProperties;
import com.rumal.poster_service.dto.PosterImagePrepareUploadItem;
import com.rumal.poster_service.dto.PosterImagePrepareUploadRequest;
import com.rumal.poster_service.dto.PosterImagePrepareUploadResponse;
import com.rumal.poster_service.dto.PosterImagePresignedUpload;
import com.rumal.poster_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
public class PosterImageStorageServiceImpl implements PosterImageStorageService {

    private static final int MAX_IMAGES_PER_REQUEST = 10;
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern KEY_PATTERN = Pattern.compile("^(posters/)?[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

    private final ObjectStorageProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    @Override
    public List<String> generateImageNames(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            throw new ValidationException("At least one file name is required");
        }
        if (fileNames.size() > MAX_IMAGES_PER_REQUEST) {
            throw new ValidationException("You can prepare at most 10 image names at once");
        }
        List<String> names = new ArrayList<>();
        for (String fileName : fileNames) {
            String extension = extractExtension(fileName);
            names.add("posters/" + UUID.randomUUID() + "." + extension);
        }
        return names;
    }

    @Override
    public List<String> uploadImages(List<MultipartFile> files, List<String> keys) {
        throw new ValidationException("Direct poster binary uploads are disabled. Use presigned uploads instead.");
    }

    @Override
    public PosterImagePrepareUploadResponse prepareUploads(PosterImagePrepareUploadRequest request) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new ValidationException("Object storage presigner is not configured");
        }
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new ValidationException("At least one image file is required");
        }
        if (request.files().size() > MAX_IMAGES_PER_REQUEST) {
            throw new ValidationException("You can prepare at most 10 uploads at once");
        }

        Instant expiresAt = Instant.now().plus(properties.presignExpiry());
        List<PosterImagePresignedUpload> uploads = new ArrayList<>();
        for (PosterImagePrepareUploadItem file : request.files()) {
            String extension = extractExtension(file.fileName());
            String contentType = normalizeRequestedContentType(file.contentType(), extension);
            if (file.sizeBytes() > MAX_FILE_SIZE_BYTES) {
                throw new ValidationException("Poster image exceeds maximum size of 5 MB");
            }
            String key = "posters/" + UUID.randomUUID() + "." + extension;
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
            uploads.add(new PosterImagePresignedUpload(
                    key,
                    presignedRequest.url().toString(),
                    contentType,
                    expiresAt
            ));
        }
        return new PosterImagePrepareUploadResponse(List.copyOf(uploads));
    }

    @Override
    public String assertImageReady(String key) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new ValidationException("Object storage client is not configured");
        }

        String normalizedKey = normalizeImageKey(key);
        String objectKey = normalizedKey.startsWith("posters/") ? normalizedKey : "posters/" + normalizedKey;
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
            if (head.contentLength() > MAX_FILE_SIZE_BYTES) {
                deleteQuietly(s3Client, objectKey);
                throw new ValidationException("Poster image exceeds maximum size of 5 MB");
            }
            String contentType = head.contentType() == null ? "" : head.contentType().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                deleteQuietly(s3Client, objectKey);
                throw new ValidationException("Poster image content type is not allowed");
            }

            byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()).asByteArray();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                deleteQuietly(s3Client, objectKey);
                throw new ValidationException("Poster image payload is not a valid image");
            }
            return objectKey;
        } catch (ValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ValidationException("Poster image is not available in object storage", ex);
        } catch (Exception ex) {
            throw new ValidationException("Poster image payload is not a valid image", ex);
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
        String normalizedKey = normalizeImageKey(key);
        StoredImage direct = fetch(s3Client, normalizedKey);
        if (direct != null) {
            return direct;
        }
        String alt = normalizedKey.startsWith("posters/")
                ? normalizedKey.substring("posters/".length())
                : "posters/" + normalizedKey;
        StoredImage fallback = fetch(s3Client, alt);
        if (fallback != null) {
            return fallback;
        }
        throw new ValidationException("Image not found");
    }

    private StoredImage fetch(S3Client s3Client, String key) {
        try {
            var bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            String contentType = bytes.response().contentType();
            return new StoredImage(bytes.asByteArray(), contentType == null ? "image/jpeg" : contentType);
        } catch (RuntimeException ex) {
            return null;
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

    private String normalizeImageKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("Image key is required");
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Invalid image key format");
        }
        return normalized;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ValidationException("Image filename is missing");
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx <= 0 || idx == originalFilename.length() - 1) {
            throw new ValidationException("Image filename must include an extension");
        }
        String ext = originalFilename.substring(idx + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Allowed image extensions: jpg, jpeg, png, webp");
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
            throw new ValidationException("Unsupported image extension");
        }
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!expected.equals(normalized)) {
            throw new ValidationException("Image content type does not match the file extension");
        }
        return expected;
    }
}
