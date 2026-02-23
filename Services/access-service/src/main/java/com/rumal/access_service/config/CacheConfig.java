package com.rumal.access_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${cache.platform-access-lookup-ttl:20s}") Duration platformAccessLookupTtl,
            @Value("${cache.vendor-access-lookup-ttl:20s}") Duration vendorAccessLookupTtl
    ) {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
                Duration ttl = switch (name) {
                    case "platformAccessLookup" -> safe(platformAccessLookupTtl, Duration.ofSeconds(20));
                    case "vendorAccessLookup" -> safe(vendorAccessLookupTtl, Duration.ofSeconds(20));
                    default -> Duration.ofSeconds(30);
                };
                return Caffeine.newBuilder()
                        .maximumSize(20_000)
                        .expireAfterWrite(ttl)
                        .build();
            }
        };
        manager.setAllowNullValues(false);
        return manager;
    }

    private Duration safe(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}

