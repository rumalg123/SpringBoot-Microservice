package com.rumal.search_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private static final String POPULAR_KEY = "search:popular";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${search.popular.max-entries:20}")
    private int maxEntries;

    public void recordSearch(String query) {
        if (query == null || query.isBlank()) return;
        String normalized = query.trim().toLowerCase();
        if (normalized.length() < 2) return;

        try {
            stringRedisTemplate.opsForZSet().incrementScore(POPULAR_KEY, normalized, 1);
            Long size = stringRedisTemplate.opsForZSet().size(POPULAR_KEY);
            if (size != null && size > maxEntries * 2) {
                stringRedisTemplate.opsForZSet().removeRange(POPULAR_KEY, 0, size - maxEntries - 1);
            }
        } catch (Exception e) {
            log.debug("Failed to record search term: {}", e.getMessage());
        }
    }

    @Cacheable(value = "popularSearches", key = "'top'")
    public List<String> getPopularSearches() {
        try {
            Set<String> results = stringRedisTemplate.opsForZSet()
                    .reverseRange(POPULAR_KEY, 0, maxEntries - 1);
            return results == null ? List.of() : new ArrayList<>(results);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> getPopularSearchesMatching(String prefix) {
        List<String> all = getPopularSearches();
        String lowerPrefix = prefix.trim().toLowerCase();
        return all.stream()
                .filter(s -> s.startsWith(lowerPrefix))
                .limit(5)
                .toList();
    }
}
