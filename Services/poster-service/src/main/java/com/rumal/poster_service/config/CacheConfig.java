package com.rumal.poster_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${cache.posters-by-placement-ttl:15s}") Duration postersTtl
    ) {
        Duration ttl = postersTtl == null || postersTtl.isNegative() || postersTtl.isZero()
                ? Duration.ofSeconds(60)
                : postersTtl;

        var postersByPlacement = new CaffeineCache(
                "postersByPlacement",
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(500)
                        .build()
        );

        var posterById = new CaffeineCache(
                "posterById",
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(1_000)
                        .build()
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(postersByPlacement, posterById));
        return manager;
    }
}
