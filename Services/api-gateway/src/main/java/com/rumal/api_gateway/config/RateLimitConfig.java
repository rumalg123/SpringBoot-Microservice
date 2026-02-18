package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class RateLimitConfig {

    private final Set<String> trustedProxyIps;

    public RateLimitConfig(
            @Value("${RATE_LIMIT_TRUSTED_PROXY_IPS:127.0.0.1,::1}") String trustedProxyIps
    ) {
        this.trustedProxyIps = Arrays.stream(trustedProxyIps.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    @Bean
    public RedisRateLimiter registerRateLimiter(
            @Value("${RATE_LIMIT_REGISTER_REPLENISH:5}") int replenishRate,
            @Value("${RATE_LIMIT_REGISTER_BURST:10}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    @Bean
    @Primary
    public RateLimiter<?> gatewayDefaultRateLimiter(
            @Value("${RATE_LIMIT_DEFAULT_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_DEFAULT_BURST:30}") int burstCapacity
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
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> "sub:" + auth.getToken().getSubject())
                .switchIfEmpty(reactor.core.publisher.Mono.just("ip:" + resolveClientIp(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.just("ip:" + resolveClientIp(exchange));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String remoteIp = extractRemoteIp(exchange);
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        String cfConnectingIp = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }

        return remoteIp;
    }

    private String extractRemoteIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        if (ip != null && ip.startsWith("::ffff:")) {
            return ip.substring("::ffff:".length());
        }
        return ip;
    }

    private boolean isTrustedProxy(String remoteIp) {
        return trustedProxyIps.contains(remoteIp);
    }
}
