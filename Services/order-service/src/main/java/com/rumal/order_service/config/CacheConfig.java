package com.rumal.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.orders-by-keycloak-ttl:60s}") Duration ordersByKeycloakTtl,
            @Value("${cache.order-details-by-keycloak-ttl:60s}") Duration orderDetailsByKeycloakTtl
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
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                valueSerializer
                        )
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(Map.of(
                        "ordersByKeycloak", defaultConfig.entryTtl(ordersByKeycloakTtl),
                        "orderDetailsByKeycloak", defaultConfig.entryTtl(orderDetailsByKeycloakTtl)
                ))
                .build();
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
