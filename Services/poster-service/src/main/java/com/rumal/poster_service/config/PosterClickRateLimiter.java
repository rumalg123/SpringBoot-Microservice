package com.rumal.poster_service.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class PosterClickRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final int maxClicksPerMinute;

    public PosterClickRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${poster.rate-limit.max-clicks-per-minute:10}") int maxClicksPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.maxClicksPerMinute = maxClicksPerMinute;
    }

    public boolean isAllowed(HttpServletRequest request, UUID posterId) {
        String ip = resolveClientIp(request);
        String key = "poster:ratelimit:" + posterId + ":" + ip;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count != null && count <= maxClicksPerMinute;
        } catch (Exception e) {
            return true;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
