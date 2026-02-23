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
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter authLogoutRateLimiter(
            @Value("${RATE_LIMIT_AUTH_LOGOUT_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_AUTH_LOGOUT_BURST:40}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter authResendVerificationRateLimiter(
            @Value("${RATE_LIMIT_AUTH_RESEND_VERIFICATION_REPLENISH:3}") int replenishRate,
            @Value("${RATE_LIMIT_AUTH_RESEND_VERIFICATION_BURST:6}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    @Primary
    public RedisRateLimiter gatewayDefaultRateLimiter(
            @Value("${RATE_LIMIT_DEFAULT_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_DEFAULT_BURST:30}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter customerMeRateLimiter(
            @Value("${RATE_LIMIT_CUSTOMER_ME_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_CUSTOMER_ME_BURST:30}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter customerAddressesRateLimiter(
            @Value("${RATE_LIMIT_CUSTOMER_ADDRESSES_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_CUSTOMER_ADDRESSES_BURST:40}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter customerAddressesWriteRateLimiter(
            @Value("${RATE_LIMIT_CUSTOMER_ADDRESSES_WRITE_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_CUSTOMER_ADDRESSES_WRITE_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter ordersMeRateLimiter(
            @Value("${RATE_LIMIT_ORDERS_ME_REPLENISH:25}") int replenishRate,
            @Value("${RATE_LIMIT_ORDERS_ME_BURST:50}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter ordersMeWriteRateLimiter(
            @Value("${RATE_LIMIT_ORDERS_ME_WRITE_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_ORDERS_ME_WRITE_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter cartMeRateLimiter(
            @Value("${RATE_LIMIT_CART_ME_REPLENISH:30}") int replenishRate,
            @Value("${RATE_LIMIT_CART_ME_BURST:60}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter cartMeWriteRateLimiter(
            @Value("${RATE_LIMIT_CART_ME_WRITE_REPLENISH:12}") int replenishRate,
            @Value("${RATE_LIMIT_CART_ME_WRITE_BURST:24}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter cartMeCheckoutRateLimiter(
            @Value("${RATE_LIMIT_CART_ME_CHECKOUT_REPLENISH:6}") int replenishRate,
            @Value("${RATE_LIMIT_CART_ME_CHECKOUT_BURST:12}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter wishlistMeRateLimiter(
            @Value("${RATE_LIMIT_WISHLIST_ME_REPLENISH:25}") int replenishRate,
            @Value("${RATE_LIMIT_WISHLIST_ME_BURST:50}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter wishlistMeWriteRateLimiter(
            @Value("${RATE_LIMIT_WISHLIST_ME_WRITE_REPLENISH:12}") int replenishRate,
            @Value("${RATE_LIMIT_WISHLIST_ME_WRITE_BURST:24}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminOrdersRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_ORDERS_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_ORDERS_BURST:40}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter productsRateLimiter(
            @Value("${RATE_LIMIT_PRODUCTS_REPLENISH:40}") int replenishRate,
            @Value("${RATE_LIMIT_PRODUCTS_BURST:80}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminProductsRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_PRODUCTS_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_PRODUCTS_BURST:40}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminProductsWriteRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_PRODUCTS_WRITE_REPLENISH:10}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_PRODUCTS_WRITE_BURST:20}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter publicCatalogAuxRateLimiter(
            @Value("${RATE_LIMIT_PUBLIC_CATALOG_AUX_REPLENISH:25}") int replenishRate,
            @Value("${RATE_LIMIT_PUBLIC_CATALOG_AUX_BURST:50}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminVendorsRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_VENDORS_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_VENDORS_BURST:30}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminVendorsWriteRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_VENDORS_WRITE_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_VENDORS_WRITE_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminPostersRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_POSTERS_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_POSTERS_BURST:30}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminPostersWriteRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_POSTERS_WRITE_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_POSTERS_WRITE_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminAccessRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_ACCESS_REPLENISH:12}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_ACCESS_BURST:24}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminAccessWriteRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_ACCESS_WRITE_REPLENISH:6}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_ACCESS_WRITE_BURST:12}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminMeRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_ME_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_ME_BURST:40}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter adminKeycloakSearchRateLimiter(
            @Value("${RATE_LIMIT_ADMIN_KEYCLOAK_SEARCH_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_ADMIN_KEYCLOAK_SEARCH_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
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

    private RedisRateLimiter redisRateLimiter(int replenishRate, int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
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
