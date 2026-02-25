package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private static final String KEY_PREFIX_USER = "person:recently-viewed:";
    private static final String KEY_PREFIX_ANON = "person:recently-viewed:anon:";

    private final StringRedisTemplate redisTemplate;
    private final ProductClient productClient;

    @Value("${personalization.recently-viewed-max:50}")
    private int maxItems;

    public void add(UUID userId, String sessionId, UUID productId) {
        String key = resolveKey(userId, sessionId);
        if (key == null) return;

        double score = System.currentTimeMillis();
        try {
            redisTemplate.opsForZSet().add(key, productId.toString(), score);
            redisTemplate.opsForZSet().removeRange(key, 0, -(maxItems + 1));
        } catch (Exception e) {
            log.warn("Failed to update recently-viewed in Redis for key={}: {}", key, e.getMessage());
        }
    }

    public List<ProductSummary> get(UUID userId, String sessionId, int limit) {
        String key = resolveKey(userId, sessionId);
        if (key == null) return List.of();

        try {
            Set<String> ids = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
            if (ids == null || ids.isEmpty()) return List.of();

            List<UUID> productIds = ids.stream().map(UUID::fromString).toList();
            List<ProductSummary> products = productClient.getBatchSummaries(productIds);

            Map<UUID, ProductSummary> productMap = new LinkedHashMap<>();
            for (ProductSummary p : products) {
                productMap.put(p.id(), p);
            }

            return productIds.stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch recently-viewed from Redis for key={}: {}", key, e.getMessage());
            return List.of();
        }
    }

    public void mergeAnonymousToUser(UUID userId, String sessionId) {
        String anonKey = KEY_PREFIX_ANON + sessionId;
        String userKey = KEY_PREFIX_USER + userId;

        try {
            Set<ZSetOperations.TypedTuple<String>> anonEntries =
                    redisTemplate.opsForZSet().rangeWithScores(anonKey, 0, -1);
            if (anonEntries != null && !anonEntries.isEmpty()) {
                Set<ZSetOperations.TypedTuple<String>> validEntries = new HashSet<>();
                for (ZSetOperations.TypedTuple<String> entry : anonEntries) {
                    if (entry.getValue() != null && entry.getScore() != null) {
                        validEntries.add(entry);
                    }
                }
                if (!validEntries.isEmpty()) {
                    redisTemplate.opsForZSet().add(userKey, validEntries);
                    redisTemplate.opsForZSet().removeRange(userKey, 0, -(maxItems + 1));
                }
            }
            redisTemplate.delete(anonKey);
        } catch (Exception e) {
            log.warn("Failed to merge recently-viewed sets: {}", e.getMessage());
        }
    }

    private String resolveKey(UUID userId, String sessionId) {
        if (userId != null) return KEY_PREFIX_USER + userId;
        if (sessionId != null) return KEY_PREFIX_ANON + sessionId;
        return null;
    }
}
