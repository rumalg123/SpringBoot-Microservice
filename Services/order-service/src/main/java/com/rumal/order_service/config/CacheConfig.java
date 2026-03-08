package com.rumal.order_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.orders-by-keycloak-ttl:60s}") Duration ordersByKeycloakTtl,
            @Value("${cache.order-details-by-keycloak-ttl:60s}") Duration orderDetailsByKeycloakTtl,
            @Value("${cache.order-analytics-platform-summary-ttl:5m}") Duration orderAnalyticsPlatformSummaryTtl,
            @Value("${cache.order-analytics-revenue-trend-ttl:5m}") Duration orderAnalyticsRevenueTrendTtl,
            @Value("${cache.order-analytics-top-products-ttl:10m}") Duration orderAnalyticsTopProductsTtl,
            @Value("${cache.order-analytics-status-breakdown-ttl:5m}") Duration orderAnalyticsStatusBreakdownTtl,
            @Value("${cache.order-analytics-vendor-summary-ttl:5m}") Duration orderAnalyticsVendorSummaryTtl,
            @Value("${cache.order-analytics-vendor-revenue-trend-ttl:5m}") Duration orderAnalyticsVendorRevenueTrendTtl,
            @Value("${cache.order-analytics-vendor-top-products-ttl:10m}") Duration orderAnalyticsVendorTopProductsTtl,
            @Value("${cache.order-analytics-customer-summary-ttl:5m}") Duration orderAnalyticsCustomerSummaryTtl,
            @Value("${cache.order-analytics-customer-spending-trend-ttl:10m}") Duration orderAnalyticsCustomerSpendingTrendTtl
    ) {
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.rumal")
                        .allowIfSubType("org.springframework.data.domain")
                        .allowIfSubType("java.util")
                        .allowIfSubType("java.time")
                        .allowIfSubType("java.math")
                        .build())
                .customize(builder -> builder.addMixIn(PageImpl.class, PageImplMixin.class))
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "os:v1::" + cacheName + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                valueSerializer
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(Map.ofEntries(
                        Map.entry("ordersByKeycloak", defaultConfig.entryTtl(ordersByKeycloakTtl)),
                        Map.entry("orderDetailsByKeycloak", defaultConfig.entryTtl(orderDetailsByKeycloakTtl)),
                        Map.entry("orderAnalyticsPlatformSummary", defaultConfig.entryTtl(orderAnalyticsPlatformSummaryTtl)),
                        Map.entry("orderAnalyticsRevenueTrend", defaultConfig.entryTtl(orderAnalyticsRevenueTrendTtl)),
                        Map.entry("orderAnalyticsTopProducts", defaultConfig.entryTtl(orderAnalyticsTopProductsTtl)),
                        Map.entry("orderAnalyticsStatusBreakdown", defaultConfig.entryTtl(orderAnalyticsStatusBreakdownTtl)),
                        Map.entry("orderAnalyticsVendorSummary", defaultConfig.entryTtl(orderAnalyticsVendorSummaryTtl)),
                        Map.entry("orderAnalyticsVendorRevenueTrend", defaultConfig.entryTtl(orderAnalyticsVendorRevenueTrendTtl)),
                        Map.entry("orderAnalyticsVendorTopProducts", defaultConfig.entryTtl(orderAnalyticsVendorTopProductsTtl)),
                        Map.entry("orderAnalyticsCustomerSummary", defaultConfig.entryTtl(orderAnalyticsCustomerSummaryTtl)),
                        Map.entry("orderAnalyticsCustomerSpendingTrend", defaultConfig.entryTtl(orderAnalyticsCustomerSpendingTrendTtl))
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
                    try {
                        cache.evict(key);
                    } catch (RuntimeException evictException) {
                        log.warn("Failed evicting corrupted cache key {} from {}", key, cache.getName(), evictException);
                    }
                    return;
                }
                throw exception;
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                throw exception;
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                throw exception;
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                throw exception;
            }
        };
    }

    @SuppressWarnings("unused")
    abstract static class PageImplMixin {
        @JsonCreator
        PageImplMixin(
                @JsonProperty("content") java.util.List<?> content,
                @JsonProperty("number") int number,
                @JsonProperty("size") int size,
                @JsonProperty("totalElements") long totalElements
        ) {
        }
    }
}
