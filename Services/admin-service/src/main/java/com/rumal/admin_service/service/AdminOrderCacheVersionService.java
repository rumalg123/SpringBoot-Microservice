package com.rumal.admin_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminOrderCacheVersionService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderCacheVersionService.class);
    private static final String DEFAULT_VERSION = "0";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public AdminOrderCacheVersionService(
            StringRedisTemplate redisTemplate,
            @Value("${cache.version-key-prefix:admin:cachever:v1::}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "admin:cachever:v1::";
    }

    public String adminOrdersVersion() {
        try {
            String value = redisTemplate.opsForValue().get(key("adminOrders"));
            return StringUtils.hasText(value) ? value : DEFAULT_VERSION;
        } catch (Exception ex) {
            return DEFAULT_VERSION;
        }
    }

    public void bumpAdminOrdersCache() {
        try {
            redisTemplate.opsForValue().increment(key("adminOrders"));
        } catch (Exception ex) {
            log.warn("Failed to bump admin order cache version (cache invalidation degraded)", ex);
        }
    }

    private String key(String bucket) {
        return keyPrefix + bucket;
    }
}
