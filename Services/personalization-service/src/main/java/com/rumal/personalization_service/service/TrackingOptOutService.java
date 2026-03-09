package com.rumal.personalization_service.service;

import com.rumal.personalization_service.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingOptOutService {

    private static final String OPT_OUT_KEY = "personalization:tracking-opt-out";

    private final StringRedisTemplate stringRedisTemplate;

    public boolean hasOptedOut(UUID userId) {
        if (userId == null) return false;
        try {
            return Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(OPT_OUT_KEY, userId.toString()));
        } catch (RuntimeException ex) {
            log.debug("Failed to check tracking opt-out for user {}: {}", userId, ex.getMessage());
            return false;
        }
    }

    public void optOut(UUID userId) {
        try {
            stringRedisTemplate.opsForSet().add(OPT_OUT_KEY, userId.toString());
            log.info("User {} opted out of personalization tracking", userId);
        } catch (RuntimeException ex) {
            log.error("Failed to record tracking opt-out for user {}: {}", userId, ex.getMessage());
            throw new ServiceUnavailableException("Failed to process opt-out request", ex);
        }
    }

    public void optIn(UUID userId) {
        try {
            stringRedisTemplate.opsForSet().remove(OPT_OUT_KEY, userId.toString());
            log.info("User {} opted back into personalization tracking", userId);
        } catch (RuntimeException ex) {
            log.error("Failed to record tracking opt-in for user {}: {}", userId, ex.getMessage());
            throw new ServiceUnavailableException("Failed to process opt-in request", ex);
        }
    }
}
