package com.rumal.review_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewCacheVersionService {

    private static final Logger log = LoggerFactory.getLogger(ReviewCacheVersionService.class);
    private static final String DEFAULT_VERSION = "0";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public ReviewCacheVersionService(
            StringRedisTemplate redisTemplate,
            @Value("${cache.key-prefix:rs:v1::}") String cacheKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = cacheKeyPrefix + "cachever::";
    }

    public String reviewByIdVersion() { return getVersion("reviewById"); }
    public String reviewsByProductVersion() { return getVersion("reviewsByProduct"); }
    public String reviewSummaryVersion() { return getVersion("reviewSummary"); }

    public void bumpAllReviewCaches() {
        bump("reviewById");
        bump("reviewsByProduct");
        bump("reviewSummary");
    }

    private String getVersion(String bucket) {
        try {
            String value = redisTemplate.opsForValue().get(key(bucket));
            return StringUtils.hasText(value) ? value : DEFAULT_VERSION;
        } catch (Exception ex) {
            return DEFAULT_VERSION;
        }
    }

    private void bump(String bucket) {
        try {
            redisTemplate.opsForValue().increment(key(bucket));
        } catch (Exception ex) {
            log.warn("Failed to bump cache version bucket={}", bucket, ex);
        }
    }

    private String key(String bucket) {
        return keyPrefix + bucket;
    }
}
