package com.rumal.wishlist_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.wishlist-by-keycloak-ttl:45s}") Duration wishlistByKeycloakTtl
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
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(30)))
                .withInitialCacheConfigurations(Map.of(
                        "wishlistByKeycloak", defaultConfig.entryTtl(wishlistByKeycloakTtl)
                ))
                .build();
    }
}
