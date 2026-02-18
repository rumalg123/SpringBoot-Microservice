package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class RateLimitConfig {

    @Bean
    public RedisRateLimiter registerRateLimiter(
            @Value("${RATE_LIMIT_REGISTER_REPLENISH:5}") int replenishRate,
            @Value("${RATE_LIMIT_REGISTER_BURST:10}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    @Bean
    public RedisRateLimiter customerMeRateLimiter(
            @Value("${RATE_LIMIT_CUSTOMER_ME_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_CUSTOMER_ME_BURST:30}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    @Bean
    public RedisRateLimiter ordersMeRateLimiter(
            @Value("${RATE_LIMIT_ORDERS_ME_REPLENISH:25}") int replenishRate,
            @Value("${RATE_LIMIT_ORDERS_ME_BURST:50}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> "sub:" + auth.getToken().getSubject())
                .switchIfEmpty(exchange.getRequest().getRemoteAddress() != null
                        ? reactor.core.publisher.Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress())
                        : reactor.core.publisher.Mono.just("ip:unknown"));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            if (exchange.getRequest().getRemoteAddress() == null) {
                return reactor.core.publisher.Mono.just("ip:unknown");
            }
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return reactor.core.publisher.Mono.just("ip:" + ip);
        };
    }
}
