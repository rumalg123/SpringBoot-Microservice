package com.rumal.poster_service.storage;

import com.rumal.poster_service.config.ObjectStorageProperties;
import com.rumal.poster_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
public class PosterImageStorageServiceImpl implements PosterImageStorageService {

    private static final int MAX_IMAGES_PER_REQUEST = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Pattern KEY_PATTERN = Pattern.compile("^(posters/)?[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp)$");

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
            names.add("posters/" + UUID.randomUUID() + "." + extension);
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

        List<String> uploaded = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String preferredKey = keys != null && keys.size() > i ? keys.get(i) : null;
            String ext = extractExtension(file.getOriginalFilename());
            String key = preferredKey == null || preferredKey.isBlank()
                    ? "posters/" + UUID.randomUUID() + "." + ext
                    : normalizeProvidedKey(preferredKey, ext);
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .cacheControl("public, max-age=31536000, immutable")
                        .contentType(resolveContentType(file, ext))
                        .build();
                s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
                uploaded.add(key);
            } catch (IOException ex) {
                throw new ValidationException("Failed to read image bytes for upload", ex);
            } catch (RuntimeException ex) {
                throw new ValidationException("Object storage upload failed", ex);
            }
        }
        return uploaded;
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

    private String normalizeProvidedKey(String key, String ext) {
        String normalized = normalizeImageKey(key);
        if (!normalized.endsWith("." + ext)) {
            throw new ValidationException("Image key extension does not match file extension");
        }
        return normalized.startsWith("posters/") ? normalized : "posters/" + normalized;
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

    private String resolveContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
