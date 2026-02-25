package com.rumal.product_service.storage;

import com.rumal.product_service.exception.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ImageUploadRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final int maxUploadsPerHour;

    public ImageUploadRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${product.image.upload.rate-limit-per-hour:60}") int maxUploadsPerHour
    ) {
        this.redisTemplate = redisTemplate;
        this.maxUploadsPerHour = maxUploadsPerHour;
    }

    public void checkRateLimit(String userSub, int imageCount) {
        if (maxUploadsPerHour <= 0) {
            return;
        }
        if (userSub == null || userSub.isBlank()) {
            throw new ValidationException("User identification required for image upload");
        }
        String key = "ps:img-upload-rate:" + userSub;
        try {
            Long current = redisTemplate.opsForValue().increment(key, imageCount);
            if (current != null && current == imageCount) {
                redisTemplate.expire(key, Duration.ofHours(1));
            }
            if (current != null && current > maxUploadsPerHour) {
                throw new ValidationException("Image upload rate limit exceeded. Max " + maxUploadsPerHour + " images per hour.");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception ignored) {
            // Redis failure should not block uploads
        }
    }
}
