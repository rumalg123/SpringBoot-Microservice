package com.rumal.product_service.storage;

import com.rumal.product_service.config.ObjectStorageProperties;
import com.rumal.product_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductImageStorageServiceImpl implements ProductImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 1_048_576;
    private static final int MAX_DIMENSION = 1200;
    private static final int MAX_IMAGES_PER_REQUEST = 5;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Pattern KEY_PATTERN = Pattern.compile("^products/[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

    private final ObjectStorageProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;

    @Override
    public List<String> generateImageNames(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            throw new ValidationException("At least one file name is required");
        }
        if (fileNames.size() > MAX_IMAGES_PER_REQUEST) {
            throw new ValidationException("You can prepare at most 5 image names at once");
        }
        List<String> names = new ArrayList<>();
        for (String fileName : fileNames) {
            String extension = extractExtension(fileName);
            names.add(buildObjectKey(extension));
        }
        return names;
    }

    @Override
    public List<String> uploadImages(List<MultipartFile> files, List<String> keys) {
        if (!properties.enabled()) {
            throw new ValidationException("Object storage is not enabled");
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new ValidationException("Object storage client is not configured");
        }
        if (files == null || files.isEmpty()) {
            throw new ValidationException("At least one image file is required");
        }
        if (files.size() > MAX_IMAGES_PER_REQUEST) {
            throw new ValidationException("You can upload at most 5 images at once");
        }
        if (keys != null && !keys.isEmpty()) {
            if (keys.size() != files.size()) {
                throw new ValidationException("files and keys size must match");
            }
            if (new LinkedHashSet<>(keys).size() != keys.size()) {
                throw new ValidationException("Duplicate image keys are not allowed");
            }
        }

        List<String> uploadedKeys = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String preferredKey = keys != null && keys.size() > i ? keys.get(i) : null;
            uploadedKeys.add(uploadOne(s3Client, files.get(i), preferredKey));
        }
        return uploadedKeys;
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
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(normalizedKey)
                    .build();
            var responseBytes = s3Client.getObjectAsBytes(request);
            String contentType = responseBytes.response().contentType();
            return new StoredImage(responseBytes.asByteArray(), contentType != null ? contentType : "image/jpeg");
        } catch (RuntimeException ex) {
            throw new ValidationException("Image not found");
        }
    }

    private String uploadOne(S3Client s3Client, MultipartFile file, String preferredKey) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Image file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("Image exceeds max size of 1MB");
        }

        String extension = extractExtension(file.getOriginalFilename());
        validateDimensions(file);

        String key = preferredKey == null || preferredKey.isBlank()
                ? buildObjectKey(extension)
                : validateAndNormalizeProvidedKey(preferredKey, extension);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .cacheControl("public, max-age=31536000, immutable")
                    .contentType(resolveContentType(file, extension))
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return key;
        } catch (IOException ex) {
            throw new ValidationException("Failed to upload image");
        }
    }

    private String buildObjectKey(String extension) {
        return "products/" + UUID.randomUUID() + "." + extension;
    }

    private String validateAndNormalizeProvidedKey(String providedKey, String extension) {
        String key = providedKey.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new ValidationException("Invalid image key format");
        }
        if (!key.endsWith("." + extension)) {
            throw new ValidationException("Image key extension does not match file extension");
        }
        return key;
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

    private void validateDimensions(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new ValidationException("Invalid image content");
            }
            if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                throw new ValidationException("Image dimensions must be at most 1200x1200");
            }
        } catch (IOException ex) {
            throw new ValidationException("Failed to read image for validation");
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ValidationException("Image filename is missing");
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx <= 0 || idx == originalFilename.length() - 1) {
            throw new ValidationException("Image filename must include an extension");
        }
        String extension = originalFilename.substring(idx + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException("Allowed image extensions: jpg, jpeg, png, webp");
        }
        return extension;
    }

    private String resolveContentType(MultipartFile file, String extension) {
        if (file.getContentType() != null && file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return file.getContentType();
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
