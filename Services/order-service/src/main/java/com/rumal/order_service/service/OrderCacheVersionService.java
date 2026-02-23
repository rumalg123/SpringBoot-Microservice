package com.rumal.order_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderCacheVersionService {

    private static final Logger log = LoggerFactory.getLogger(OrderCacheVersionService.class);
    private static final String DEFAULT_VERSION = "0";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public OrderCacheVersionService(
            StringRedisTemplate redisTemplate,
            @Value("${cache.version-key-prefix:os:cachever:v1::}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "os:cachever:v1::";
    }

    public String ordersByKeycloakVersion() {
        return getVersion("ordersByKeycloak");
    }

    public String orderDetailsByKeycloakVersion() {
        return getVersion("orderDetailsByKeycloak");
    }

    public void bumpOrdersByKeycloakCache() {
        bump("ordersByKeycloak");
    }

    public void bumpOrderDetailsByKeycloakCache() {
        bump("orderDetailsByKeycloak");
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
            log.warn("Failed to bump order cache version bucket={} (cache invalidation degraded)", bucket, ex);
        }
    }

    private String key(String bucket) {
        return keyPrefix + bucket;
    }
}
