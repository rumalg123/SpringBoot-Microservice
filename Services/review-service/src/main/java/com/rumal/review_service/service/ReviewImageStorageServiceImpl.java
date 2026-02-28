package com.rumal.review_service.service;

import com.rumal.review_service.config.ObjectStorageProperties;
import com.rumal.review_service.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ReviewImageStorageServiceImpl implements ReviewImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ReviewImageStorageServiceImpl.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_FILE_SIZE = 1_048_576; // 1MB
    private static final int MAX_FILES = 5;
    private static final int THUMBNAIL_MAX_DIM = 300;
    private static final Pattern KEY_PATTERN = Pattern.compile("^(reviews/)?[A-Za-z0-9-]+(-thumb)?\\.(jpg|jpeg|png|webp)$");

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectStorageProperties properties;

    public ReviewImageStorageServiceImpl(ObjectProvider<S3Client> s3ClientProvider, ObjectStorageProperties properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.properties = properties;
    }

    @Override
    public List<String> uploadImages(List<MultipartFile> files) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null) {
            throw new ValidationException("Image storage is not configured");
        }
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > MAX_FILES) {
            throw new ValidationException("Cannot upload more than " + MAX_FILES + " images");
        }

        List<String> keys = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new ValidationException("File " + file.getOriginalFilename() + " exceeds 1MB limit");
            }
            String ext = getExtension(file.getOriginalFilename());
            if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new ValidationException("File type ." + ext + " is not allowed. Allowed: jpg, jpeg, png, webp");
            }

            try {
                byte[] originalBytes = file.getBytes();

                // Validate actual image content before uploading
                try (ByteArrayInputStream probe = new ByteArrayInputStream(originalBytes)) {
                    BufferedImage img = ImageIO.read(probe);
                    if (img == null) {
                        throw new ValidationException("File " + file.getOriginalFilename() + " is not a valid image");
                    }
                }

                String key = "reviews/" + UUID.randomUUID() + "." + ext;
                String contentType = file.getContentType() != null ? file.getContentType() : "image/" + ext;

                s3.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .cacheControl("public, max-age=31536000, immutable")
                        .contentType(contentType)
                        .build(), RequestBody.fromBytes(originalBytes));

                // Generate and upload thumbnail
                try {
                    byte[] thumbBytes = generateThumbnail(originalBytes, ext);
                    if (thumbBytes != null) {
                        String thumbKey = key.replace("." + ext, "-thumb." + ext);
                        s3.putObject(PutObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(thumbKey)
                                .cacheControl("public, max-age=31536000, immutable")
                                .contentType(contentType)
                                .build(), RequestBody.fromBytes(thumbBytes));
                    }
                } catch (Exception thumbEx) {
                    log.warn("Failed to generate thumbnail for {}", key, thumbEx);
                }

                keys.add(key);
            } catch (IOException e) {
                throw new ValidationException("Failed to read file: " + file.getOriginalFilename());
            }
        }
        return keys;
    }

    @Override
    public void deleteImages(List<String> keys) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null || keys == null) return;
        for (String key : keys) {
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
                String ext = getExtension(key);
                String thumbKey = key.replace("." + ext, "-thumb." + ext);
                s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(thumbKey).build());
            } catch (Exception ex) {
                log.warn("Failed to delete image {}", key, ex);
            }
        }
    }

    @Override
    public StoredImage getImage(String key) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null) {
            throw new ValidationException("Image storage is not configured");
        }
        if (key == null || key.isBlank()) {
            throw new ValidationException("Image key is required");
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Invalid image key format");
        }

        StoredImage direct = fetchImage(s3, normalized);
        if (direct != null) return direct;

        // Try with/without prefix
        String alternate = normalized.startsWith("reviews/")
                ? normalized.substring("reviews/".length())
                : "reviews/" + normalized;
        StoredImage alt = fetchImage(s3, alternate);
        if (alt != null) return alt;

        throw new ValidationException("Image not found");
    }

    private StoredImage fetchImage(S3Client s3, String key) {
        try {
            var responseBytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            String contentType = responseBytes.response().contentType();
            return new StoredImage(responseBytes.asByteArray(), contentType != null ? contentType : "image/jpeg");
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private byte[] generateThumbnail(byte[] originalBytes, String ext) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) return null;
        int w = original.getWidth(), h = original.getHeight();
        if (w <= THUMBNAIL_MAX_DIM && h <= THUMBNAIL_MAX_DIM) return null;
        double scale = Math.min((double) THUMBNAIL_MAX_DIM / w, (double) THUMBNAIL_MAX_DIM / h);
        int tw = (int) (w * scale), th = (int) (h * scale);
        BufferedImage thumb = new BufferedImage(tw, th, original.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : original.getType());
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, tw, th, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = ext.equals("jpg") ? "jpeg" : ext;
        ImageIO.write(thumb, format, baos);
        return baos.toByteArray();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
