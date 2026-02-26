package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

@Configuration
public class RateLimitConfig {

    private final Set<String> trustedProxyExactIps;
    private final List<CidrRange> trustedProxyCidrs;

    public RateLimitConfig(
            @Value("${RATE_LIMIT_TRUSTED_PROXY_IPS:127.0.0.1,::1}") String trustedProxyIps
    ) {
        Set<String> exactIps = new java.util.HashSet<>();
        List<CidrRange> cidrs = new java.util.ArrayList<>();

        for (String entry : trustedProxyIps.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) continue;
            if (trimmed.contains("/")) {
                CidrRange range = CidrRange.parse(trimmed);
                if (range != null) cidrs.add(range);
            } else {
                exactIps.add(trimmed);
            }
        }
        this.trustedProxyExactIps = Set.copyOf(exactIps);
        this.trustedProxyCidrs = List.copyOf(cidrs);
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
    public RedisRateLimiter publicPromotionsRateLimiter(
            @Value("${RATE_LIMIT_PUBLIC_PROMOTIONS_REPLENISH:20}") int replenishRate,
            @Value("${RATE_LIMIT_PUBLIC_PROMOTIONS_BURST:40}") int burstCapacity
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
    public RedisRateLimiter vendorMeRateLimiter(
            @Value("${RATE_LIMIT_VENDOR_ME_REPLENISH:15}") int replenishRate,
            @Value("${RATE_LIMIT_VENDOR_ME_BURST:30}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter vendorMeWriteRateLimiter(
            @Value("${RATE_LIMIT_VENDOR_ME_WRITE_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_VENDOR_ME_WRITE_BURST:16}") int burstCapacity
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
    public RedisRateLimiter paymentMeRateLimiter(
            @Value("${RATE_LIMIT_PAYMENT_ME_REPLENISH:8}") int replenishRate,
            @Value("${RATE_LIMIT_PAYMENT_ME_BURST:16}") int burstCapacity
    ) {
        return redisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public RedisRateLimiter paymentMeWriteRateLimiter(
            @Value("${RATE_LIMIT_PAYMENT_ME_WRITE_REPLENISH:4}") int replenishRate,
            @Value("${RATE_LIMIT_PAYMENT_ME_WRITE_BURST:8}") int burstCapacity
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
        if (trustedProxyExactIps.contains(remoteIp)) {
            return true;
        }
        if (trustedProxyCidrs.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteIp);
            byte[] addrBytes = addr.getAddress();
            for (CidrRange cidr : trustedProxyCidrs) {
                if (cidr.contains(addrBytes)) return true;
            }
        } catch (UnknownHostException ignored) {
        }
        return false;
    }

    private record CidrRange(byte[] network, int prefixLength) {
        static CidrRange parse(String cidr) {
            try {
                String[] parts = cidr.split("/");
                InetAddress addr = InetAddress.getByName(parts[0].trim());
                int prefix = Integer.parseInt(parts[1].trim());
                return new CidrRange(addr.getAddress(), prefix);
            } catch (Exception e) {
                return null;
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) return false;
            }
            if (remainingBits > 0 && fullBytes < network.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((address[fullBytes] & mask) != (network[fullBytes] & mask)) return false;
            }
            return true;
        }
    }
}
