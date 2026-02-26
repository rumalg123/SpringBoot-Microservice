package com.rumal.promotion_service.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper,
            @Value("${cache.promotion-by-id-ttl:120s}") Duration promotionByIdTtl,
            @Value("${cache.promotion-admin-list-ttl:20s}") Duration promotionAdminListTtl,
            @Value("${cache.public-promotion-list-ttl:30s}") Duration publicPromotionListTtl,
            @Value("${cache.public-promotion-by-id-ttl:120s}") Duration publicPromotionByIdTtl,
            @Value("${cache.key-prefix:promo:v1::}") String cacheKeyPrefix
    ) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.addMixIn(PageImpl.class, PageImplMixin.class);

        GenericJackson2JsonRedisSerializer valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(cacheObjectMapper)
                .defaultTyping(true)
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> cacheKeyPrefix + cacheName + "::")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(Map.of(
                        "promotionById", defaultConfig.entryTtl(promotionByIdTtl),
                        "promotionAdminList", defaultConfig.entryTtl(promotionAdminListTtl),
                        "publicPromotionList", defaultConfig.entryTtl(publicPromotionListTtl),
                        "publicPromotionById", defaultConfig.entryTtl(publicPromotionByIdTtl)
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

    @Bean
    public ApplicationRunner cacheStartupCleaner(
            CacheManager cacheManager,
            @Value("${cache.clear-on-startup:false}") boolean clearOnStartup
    ) {
        return args -> {
            if (!clearOnStartup) {
                return;
            }
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
            log.info("Cleared all Redis caches on startup (cache.clear-on-startup={})", clearOnStartup);
        };
    }

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
