package com.rumal.personalization_service.service;

import com.rumal.personalization_service.dto.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationProfileService {

    private static final String USER_CATEGORY_KEY_PREFIX = "person:profile:user:cat:";
    private static final String USER_BRAND_KEY_PREFIX = "person:profile:user:brand:";
    private static final String USER_PURCHASED_KEY_PREFIX = "person:profile:user:purchased:";
    private static final String SESSION_CATEGORY_KEY_PREFIX = "person:profile:session:cat:";
    private static final String SESSION_LINK_KEY_PREFIX = "person:profile:session:user:";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${personalization.profile.session-ttl:30d}")
    private Duration anonymousProfileTtl;

    @Value("${personalization.profile.user-purchase-ttl:35d}")
    private Duration userPurchaseTtl;

    @Value("${personalization.profile.session-link-ttl:30d}")
    private Duration sessionLinkTtl;

    public void recordEvent(
            UUID userId,
            String sessionId,
            EventType eventType,
            UUID productId,
            Collection<String> categories,
            String brandName,
            Instant occurredAt
    ) {
        if (eventType == null || productId == null) {
            return;
        }

        double weight = eventWeight(eventType);
        if (weight <= 0) {
            return;
        }

        try {
            if (userId != null) {
                updateUserAffinities(userId, categories, brandName, weight);
                if (eventType == EventType.PURCHASE) {
                    rememberRecentPurchase(userId, productId, occurredAt);
                }
                return;
            }

            if (StringUtils.hasText(sessionId)) {
                updateSessionAffinities(sessionId.trim(), categories, weight);
            }
        } catch (Exception ex) {
            log.warn("Failed to update personalization profile state for user={} session={}: {}",
                    userId, sessionId, ex.getMessage());
        }
    }

    public List<String> getTopUserCategories(UUID userId, int limit) {
        return readTopMembers(USER_CATEGORY_KEY_PREFIX + userId, limit);
    }

    public List<String> getTopUserBrands(UUID userId, int limit) {
        return readTopMembers(USER_BRAND_KEY_PREFIX + userId, limit);
    }

    public List<String> getTopSessionCategories(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        return readTopMembers(SESSION_CATEGORY_KEY_PREFIX + sessionId.trim(), limit);
    }

    public Set<UUID> getRecentPurchasedProductIds(UUID userId, Instant since) {
        if (userId == null || since == null) {
            return Set.of();
        }

        try {
            Set<String> ids = stringRedisTemplate.opsForZSet().reverseRangeByScore(
                    USER_PURCHASED_KEY_PREFIX + userId,
                    since.toEpochMilli(),
                    Double.POSITIVE_INFINITY
            );
            if (ids == null || ids.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new LinkedHashSet<>();
            for (String id : ids) {
                UUID parsed = parseUuid(id);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to read recent purchases for user {}: {}", userId, ex.getMessage());
            return Set.of();
        }
    }

    public void rememberMergedSession(UUID userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }

        try {
            String key = SESSION_LINK_KEY_PREFIX + sessionId.trim();
            stringRedisTemplate.opsForValue().set(key, userId.toString(), sessionLinkTtl);
        } catch (Exception ex) {
            log.warn("Failed to remember merged session {} -> {}: {}", sessionId, userId, ex.getMessage());
        }
    }

    public Map<String, UUID> resolveMergedUsers(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }

        Map<String, UUID> resolved = new LinkedHashMap<>();
        for (String rawSessionId : sessionIds) {
            if (!StringUtils.hasText(rawSessionId)) {
                continue;
            }

            String sessionId = rawSessionId.trim();
            try {
                String rawUserId = stringRedisTemplate.opsForValue().get(SESSION_LINK_KEY_PREFIX + sessionId);
                UUID userId = parseUuid(rawUserId);
                if (userId != null) {
                    resolved.put(sessionId, userId);
                }
            } catch (Exception ex) {
                log.debug("Failed to resolve merged session {}: {}", sessionId, ex.getMessage());
            }
        }
        return resolved;
    }

    public void mergeAnonymousToUser(UUID userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }

        String normalizedSessionId = sessionId.trim();
        mergeScores(SESSION_CATEGORY_KEY_PREFIX + normalizedSessionId, USER_CATEGORY_KEY_PREFIX + userId, anonymousProfileTtl);
        rememberMergedSession(userId, normalizedSessionId);
        deleteKeys(List.of(SESSION_CATEGORY_KEY_PREFIX + normalizedSessionId));
    }

    private void updateUserAffinities(UUID userId, Collection<String> categories, String brandName, double weight) {
        incrementMembers(USER_CATEGORY_KEY_PREFIX + userId, categories, weight, anonymousProfileTtl);
        if (StringUtils.hasText(brandName)) {
            incrementMembers(USER_BRAND_KEY_PREFIX + userId, List.of(brandName.trim()), weight, anonymousProfileTtl);
        }
    }

    private void updateSessionAffinities(String sessionId, Collection<String> categories, double weight) {
        incrementMembers(SESSION_CATEGORY_KEY_PREFIX + sessionId, categories, weight, anonymousProfileTtl);
    }

    private void rememberRecentPurchase(UUID userId, UUID productId, Instant occurredAt) {
        String key = USER_PURCHASED_KEY_PREFIX + userId;
        double timestamp = occurredAt == null ? System.currentTimeMillis() : occurredAt.toEpochMilli();
        stringRedisTemplate.opsForZSet().add(key, productId.toString(), timestamp);
        Instant cutoff = Instant.ofEpochMilli((long) timestamp).minus(userPurchaseTtl);
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff.toEpochMilli());
        stringRedisTemplate.expire(key, userPurchaseTtl);
    }

    private void incrementMembers(String key, Collection<String> rawMembers, double weight, Duration ttl) {
        if (rawMembers == null || rawMembers.isEmpty()) {
            return;
        }

        Set<String> members = new LinkedHashSet<>();
        for (String rawMember : rawMembers) {
            if (StringUtils.hasText(rawMember)) {
                members.add(rawMember.trim());
            }
        }

        if (members.isEmpty()) {
            return;
        }

        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        for (String member : members) {
            zSetOperations.incrementScore(key, member, weight);
        }
        stringRedisTemplate.expire(key, ttl);
    }

    private void mergeScores(String sourceKey, String targetKey, Duration ttl) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .rangeWithScores(sourceKey, 0, -1);
            if (tuples == null || tuples.isEmpty()) {
                return;
            }

            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String value = tuple.getValue();
                Double score = tuple.getScore();
                if (!StringUtils.hasText(value) || score == null) {
                    continue;
                }
                stringRedisTemplate.opsForZSet().incrementScore(targetKey, value.trim(), score);
            }
            stringRedisTemplate.expire(targetKey, ttl);
        } catch (Exception ex) {
            log.warn("Failed to merge recommendation profile state from {} into {}: {}",
                    sourceKey, targetKey, ex.getMessage());
        }
    }

    private void deleteKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        try {
            stringRedisTemplate.delete(keys);
        } catch (Exception ex) {
            log.warn("Failed to delete personalization profile keys {}: {}", keys, ex.getMessage());
        }
    }

    private List<String> readTopMembers(String key, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        try {
            Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1L);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            return List.copyOf(members);
        } catch (Exception ex) {
            log.warn("Failed to read personalization profile key {}: {}", key, ex.getMessage());
            return List.of();
        }
    }

    private double eventWeight(EventType eventType) {
        return switch (eventType) {
            case PURCHASE -> 10.0;
            case WISHLIST_ADD -> 5.0;
            case ADD_TO_CART -> 3.0;
            case PRODUCT_VIEW, SEARCH -> 1.0;
        };
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
