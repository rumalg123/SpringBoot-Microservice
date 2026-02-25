package com.rumal.analytics_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.key-prefix:analytics:v1::}") String cacheKeyPrefix,
            @Value("${cache.dashboard-summary-ttl:5m}") Duration dashboardSummaryTtl,
            @Value("${cache.revenue-summary-ttl:5m}") Duration revenueSummaryTtl,
            @Value("${cache.top-products-ttl:10m}") Duration topProductsTtl,
            @Value("${cache.vendor-leaderboard-ttl:10m}") Duration vendorLeaderboardTtl,
            @Value("${cache.customer-segmentation-ttl:15m}") Duration customerSegmentationTtl,
            @Value("${cache.inventory-health-ttl:5m}") Duration inventoryHealthTtl,
            @Value("${cache.promotion-roi-ttl:10m}") Duration promotionRoiTtl,
            @Value("${cache.review-analytics-ttl:10m}") Duration reviewAnalyticsTtl,
            @Value("${cache.vendor-analytics-ttl:5m}") Duration vendorAnalyticsTtl,
            @Value("${cache.customer-insights-ttl:5m}") Duration customerInsightsTtl
    ) {
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.rumal")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.time")
                        .allowIfSubType("java.math")
                        .build())
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> cacheKeyPrefix + cacheName + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(Map.of(
                        "dashboardSummary", defaultConfig.entryTtl(dashboardSummaryTtl),
                        "revenueSummary", defaultConfig.entryTtl(revenueSummaryTtl),
                        "topProducts", defaultConfig.entryTtl(topProductsTtl),
                        "vendorLeaderboard", defaultConfig.entryTtl(vendorLeaderboardTtl),
                        "customerSegmentation", defaultConfig.entryTtl(customerSegmentationTtl),
                        "inventoryHealth", defaultConfig.entryTtl(inventoryHealthTtl),
                        "promotionRoi", defaultConfig.entryTtl(promotionRoiTtl),
                        "reviewAnalytics", defaultConfig.entryTtl(reviewAnalyticsTtl),
                        "vendorAnalytics", defaultConfig.entryTtl(vendorAnalyticsTtl),
                        "customerInsights", defaultConfig.entryTtl(customerInsightsTtl)
                ))
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                if (exception instanceof SerializationException) {
                    log.warn("Ignoring Redis cache read error on cache={} key={}. Evicting corrupted entry.", cache.getName(), key);
                    try { cache.evict(key); } catch (RuntimeException e) { log.warn("Failed evicting corrupted cache key {} from {}", key, cache.getName(), e); }
                    return;
                }
                throw exception;
            }
            @Override public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) { throw exception; }
            @Override public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) { throw exception; }
            @Override public void handleCacheClearError(RuntimeException exception, Cache cache) { throw exception; }
        };
    }

    @Bean
    public ApplicationRunner cacheStartupCleaner(
            CacheManager cacheManager,
            @Value("${cache.clear-on-startup:true}") boolean clearOnStartup
    ) {
        return args -> {
            if (!clearOnStartup) return;
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) cache.clear();
            }
            log.info("Cleared all Redis caches on startup (cache.clear-on-startup={})", clearOnStartup);
        };
    }
}
