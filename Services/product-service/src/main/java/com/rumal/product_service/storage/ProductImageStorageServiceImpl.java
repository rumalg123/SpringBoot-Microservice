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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
    private static final int THUMBNAIL_MAX_DIMENSION = 300;
    private static final int MAX_IMAGES_PER_REQUEST = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Pattern KEY_PATTERN = Pattern.compile("^(products/)?[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

    private final ObjectStorageProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;

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
            throw new ValidationException("You can upload at most 10 images at once");
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
        StoredImage direct = fetchImage(s3Client, normalizedKey);
        if (direct != null) {
            return direct;
        }

        String alternateKey = normalizedKey.startsWith("products/")
                ? normalizedKey.substring("products/".length())
                : "products/" + normalizedKey;
        if (!alternateKey.equals(normalizedKey)) {
            StoredImage alternate = fetchImage(s3Client, alternateKey);
            if (alternate != null) {
                return alternate;
            }
        }

        throw new ValidationException("Image not found");
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
            String contentType = resolveContentType(file, extension);
            byte[] originalBytes = file.getBytes();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .cacheControl("public, max-age=31536000, immutable")
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(originalBytes));

            generateAndUploadThumbnail(s3Client, file, key, extension, contentType);

            return key;
        } catch (IOException ex) {
            throw new ValidationException("Failed to upload image");
        }
    }

    private void generateAndUploadThumbnail(S3Client s3Client, MultipartFile file, String originalKey, String extension, String contentType) {
        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                return;
            }
            int w = original.getWidth();
            int h = original.getHeight();
            if (w <= THUMBNAIL_MAX_DIMENSION && h <= THUMBNAIL_MAX_DIMENSION) {
                return;
            }

            double scale = Math.min((double) THUMBNAIL_MAX_DIMENSION / w, (double) THUMBNAIL_MAX_DIMENSION / h);
            int newW = (int) (w * scale);
            int newH = (int) (h * scale);

            BufferedImage thumbnail = new BufferedImage(newW, newH, original.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : original.getType());
            Graphics2D g = thumbnail.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, newW, newH, null);
            g.dispose();

            String format = "png".equals(extension) ? "png" : "jpg";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, format, baos);

            String thumbKey = buildThumbnailKey(originalKey);
            PutObjectRequest thumbRequest = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(thumbKey)
                    .cacheControl("public, max-age=31536000, immutable")
                    .contentType(contentType)
                    .build();
            s3Client.putObject(thumbRequest, RequestBody.fromBytes(baos.toByteArray()));
        } catch (IOException ignored) {
            // Thumbnail generation is best-effort; don't fail the upload
        }
    }

    private String buildThumbnailKey(String originalKey) {
        int dotIdx = originalKey.lastIndexOf('.');
        return originalKey.substring(0, dotIdx) + "-thumb" + originalKey.substring(dotIdx);
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

    private StoredImage fetchImage(S3Client s3Client, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build();
            var responseBytes = s3Client.getObjectAsBytes(request);
            String contentType = responseBytes.response().contentType();
            return new StoredImage(responseBytes.asByteArray(), contentType != null ? contentType : "image/jpeg");
        } catch (RuntimeException ignored) {
            return null;
        }
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
