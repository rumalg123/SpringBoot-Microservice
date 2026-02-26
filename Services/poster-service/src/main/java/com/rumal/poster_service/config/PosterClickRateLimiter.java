package com.rumal.poster_service.config;

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

    public boolean isAllowed(String ip, UUID posterId) {
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
}
