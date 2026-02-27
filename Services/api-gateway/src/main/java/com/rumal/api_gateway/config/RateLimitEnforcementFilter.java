package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@NullMarked
public class RateLimitEnforcementFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RateLimitEnforcementFilter.class);

    private final RedisRateLimiter registerRateLimiter;
    private final RedisRateLimiter authLogoutRateLimiter;
    private final RedisRateLimiter authResendVerificationRateLimiter;
    private final RedisRateLimiter customerMeRateLimiter;
    private final RedisRateLimiter customerAddressesRateLimiter;
    private final RedisRateLimiter customerAddressesWriteRateLimiter;
    private final RedisRateLimiter ordersMeRateLimiter;
    private final RedisRateLimiter ordersMeWriteRateLimiter;
    private final RedisRateLimiter cartMeRateLimiter;
    private final RedisRateLimiter cartMeWriteRateLimiter;
    private final RedisRateLimiter cartMeCheckoutRateLimiter;
    private final RedisRateLimiter wishlistMeRateLimiter;
    private final RedisRateLimiter wishlistMeWriteRateLimiter;
    private final RedisRateLimiter adminOrdersRateLimiter;
    private final RedisRateLimiter productsRateLimiter;
    private final RedisRateLimiter adminProductsRateLimiter;
    private final RedisRateLimiter adminProductsWriteRateLimiter;
    private final RedisRateLimiter publicPromotionsRateLimiter;
    private final RedisRateLimiter publicCatalogAuxRateLimiter;
    private final RedisRateLimiter vendorMeRateLimiter;
    private final RedisRateLimiter vendorMeWriteRateLimiter;
    private final RedisRateLimiter adminVendorsRateLimiter;
    private final RedisRateLimiter adminVendorsWriteRateLimiter;
    private final RedisRateLimiter adminPostersRateLimiter;
    private final RedisRateLimiter adminPostersWriteRateLimiter;
    private final RedisRateLimiter adminAccessRateLimiter;
    private final RedisRateLimiter adminAccessWriteRateLimiter;
    private final RedisRateLimiter adminMeRateLimiter;
    private final RedisRateLimiter adminKeycloakSearchRateLimiter;
    private final RedisRateLimiter paymentMeRateLimiter;
    private final RedisRateLimiter paymentMeWriteRateLimiter;
    private final RedisRateLimiter webhookRateLimiter;
    private final RedisRateLimiter gatewayDefaultRateLimiter;
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userOrIpKeyResolver;
    private final ObjectMapper objectMapper;
    private final boolean failOpenOnRateLimiterError;

    public RateLimitEnforcementFilter(
            @Qualifier("registerRateLimiter") RedisRateLimiter registerRateLimiter,
            @Qualifier("authLogoutRateLimiter") RedisRateLimiter authLogoutRateLimiter,
            @Qualifier("authResendVerificationRateLimiter") RedisRateLimiter authResendVerificationRateLimiter,
            @Qualifier("customerMeRateLimiter") RedisRateLimiter customerMeRateLimiter,
            @Qualifier("customerAddressesRateLimiter") RedisRateLimiter customerAddressesRateLimiter,
            @Qualifier("customerAddressesWriteRateLimiter") RedisRateLimiter customerAddressesWriteRateLimiter,
            @Qualifier("ordersMeRateLimiter") RedisRateLimiter ordersMeRateLimiter,
            @Qualifier("ordersMeWriteRateLimiter") RedisRateLimiter ordersMeWriteRateLimiter,
            @Qualifier("cartMeRateLimiter") RedisRateLimiter cartMeRateLimiter,
            @Qualifier("cartMeWriteRateLimiter") RedisRateLimiter cartMeWriteRateLimiter,
            @Qualifier("cartMeCheckoutRateLimiter") RedisRateLimiter cartMeCheckoutRateLimiter,
            @Qualifier("wishlistMeRateLimiter") RedisRateLimiter wishlistMeRateLimiter,
            @Qualifier("wishlistMeWriteRateLimiter") RedisRateLimiter wishlistMeWriteRateLimiter,
            @Qualifier("adminOrdersRateLimiter") RedisRateLimiter adminOrdersRateLimiter,
            @Qualifier("productsRateLimiter") RedisRateLimiter productsRateLimiter,
            @Qualifier("adminProductsRateLimiter") RedisRateLimiter adminProductsRateLimiter,
            @Qualifier("adminProductsWriteRateLimiter") RedisRateLimiter adminProductsWriteRateLimiter,
            @Qualifier("publicPromotionsRateLimiter") RedisRateLimiter publicPromotionsRateLimiter,
            @Qualifier("publicCatalogAuxRateLimiter") RedisRateLimiter publicCatalogAuxRateLimiter,
            @Qualifier("vendorMeRateLimiter") RedisRateLimiter vendorMeRateLimiter,
            @Qualifier("vendorMeWriteRateLimiter") RedisRateLimiter vendorMeWriteRateLimiter,
            @Qualifier("adminVendorsRateLimiter") RedisRateLimiter adminVendorsRateLimiter,
            @Qualifier("adminVendorsWriteRateLimiter") RedisRateLimiter adminVendorsWriteRateLimiter,
            @Qualifier("adminPostersRateLimiter") RedisRateLimiter adminPostersRateLimiter,
            @Qualifier("adminPostersWriteRateLimiter") RedisRateLimiter adminPostersWriteRateLimiter,
            @Qualifier("adminAccessRateLimiter") RedisRateLimiter adminAccessRateLimiter,
            @Qualifier("adminAccessWriteRateLimiter") RedisRateLimiter adminAccessWriteRateLimiter,
            @Qualifier("adminMeRateLimiter") RedisRateLimiter adminMeRateLimiter,
            @Qualifier("adminKeycloakSearchRateLimiter") RedisRateLimiter adminKeycloakSearchRateLimiter,
            @Qualifier("paymentMeRateLimiter") RedisRateLimiter paymentMeRateLimiter,
            @Qualifier("paymentMeWriteRateLimiter") RedisRateLimiter paymentMeWriteRateLimiter,
            @Qualifier("webhookRateLimiter") RedisRateLimiter webhookRateLimiter,
            @Qualifier("gatewayDefaultRateLimiter") RedisRateLimiter gatewayDefaultRateLimiter,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver,
            ObjectMapper objectMapper,
            @Value("${rate-limit.fail-open-on-error:true}") boolean failOpenOnRateLimiterError
    ) {
        this.registerRateLimiter = registerRateLimiter;
        this.authLogoutRateLimiter = authLogoutRateLimiter;
        this.authResendVerificationRateLimiter = authResendVerificationRateLimiter;
        this.customerMeRateLimiter = customerMeRateLimiter;
        this.customerAddressesRateLimiter = customerAddressesRateLimiter;
        this.customerAddressesWriteRateLimiter = customerAddressesWriteRateLimiter;
        this.ordersMeRateLimiter = ordersMeRateLimiter;
        this.ordersMeWriteRateLimiter = ordersMeWriteRateLimiter;
        this.cartMeRateLimiter = cartMeRateLimiter;
        this.cartMeWriteRateLimiter = cartMeWriteRateLimiter;
        this.cartMeCheckoutRateLimiter = cartMeCheckoutRateLimiter;
        this.wishlistMeRateLimiter = wishlistMeRateLimiter;
        this.wishlistMeWriteRateLimiter = wishlistMeWriteRateLimiter;
        this.adminOrdersRateLimiter = adminOrdersRateLimiter;
        this.productsRateLimiter = productsRateLimiter;
        this.adminProductsRateLimiter = adminProductsRateLimiter;
        this.adminProductsWriteRateLimiter = adminProductsWriteRateLimiter;
        this.publicPromotionsRateLimiter = publicPromotionsRateLimiter;
        this.publicCatalogAuxRateLimiter = publicCatalogAuxRateLimiter;
        this.vendorMeRateLimiter = vendorMeRateLimiter;
        this.vendorMeWriteRateLimiter = vendorMeWriteRateLimiter;
        this.adminVendorsRateLimiter = adminVendorsRateLimiter;
        this.adminVendorsWriteRateLimiter = adminVendorsWriteRateLimiter;
        this.adminPostersRateLimiter = adminPostersRateLimiter;
        this.adminPostersWriteRateLimiter = adminPostersWriteRateLimiter;
        this.adminAccessRateLimiter = adminAccessRateLimiter;
        this.adminAccessWriteRateLimiter = adminAccessWriteRateLimiter;
        this.adminMeRateLimiter = adminMeRateLimiter;
        this.adminKeycloakSearchRateLimiter = adminKeycloakSearchRateLimiter;
        this.paymentMeRateLimiter = paymentMeRateLimiter;
        this.paymentMeWriteRateLimiter = paymentMeWriteRateLimiter;
        this.webhookRateLimiter = webhookRateLimiter;
        this.gatewayDefaultRateLimiter = gatewayDefaultRateLimiter;
        this.ipKeyResolver = ipKeyResolver;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
        this.objectMapper = objectMapper;
        this.failOpenOnRateLimiterError = failOpenOnRateLimiterError;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        Policy policy = resolvePolicy(path, method);

        return policy.keyResolver().resolve(exchange)
                .defaultIfEmpty("ip:unknown")
                .flatMap(key -> policy.rateLimiter().isAllowed(policy.id(), key)
                        .map(RateLimitDecision::allowed)
                        .onErrorResume(ex -> Mono.just(RateLimitDecision.error(ex))))
                .flatMap(decision -> {
                    if (decision.error() != null) {
                        return handleRateLimiterError(exchange, chain, policy.id(), decision.error());
                    }
                    RateLimiter.Response result = decision.response();
                    if (result == null) {
                        return handleRateLimiterError(exchange, chain, policy.id(),
                                new IllegalStateException("Rate limiter returned no decision"));
                    }
                    result.getHeaders().forEach((header, value) ->
                            exchange.getResponse().getHeaders().add(header, value)
                    );
                    exchange.getResponse().getHeaders().set("X-RateLimit-Policy", policy.id());
                    if (result.isAllowed()) {
                        return chain.filter(exchange);
                    }
                    return writeTooManyRequests(exchange, policy.id());
                });
    }

    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange, String policyId) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded");
        body.put("policyId", policyId);
        body.put("requestId", requestId != null ? requestId : "");

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "1");
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(toJsonBytes(body, 429));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private Mono<Void> handleRateLimiterError(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String policyId,
            Throwable error
    ) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        log.warn("Rate limiter unavailable for policy={} path={} requestId={} failOpen={}",
                policyId, path, requestId, failOpenOnRateLimiterError, error);

        exchange.getResponse().getHeaders().set("X-RateLimit-Policy", policyId);
        exchange.getResponse().getHeaders().set("X-RateLimit-Status", "ERROR");

        if (failOpenOnRateLimiterError) {
            exchange.getResponse().getHeaders().set("X-RateLimit-Bypass", "RATE_LIMITER_UNAVAILABLE");
            return chain.filter(exchange);
        }
        return writeRateLimitServiceUnavailable(exchange, policyId);
    }

    private Mono<Void> writeRateLimitServiceUnavailable(ServerWebExchange exchange, String policyId) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "Rate limiting service unavailable");
        body.put("policyId", policyId);
        body.put("requestId", requestId != null ? requestId : "");

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(toJsonBytes(body, 503));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private Policy resolvePolicy(String path, @Nullable HttpMethod method) {
        if (("/customers/register".equals(path) || "/customers/register-identity".equals(path))
                && method == HttpMethod.POST) {
            return new Policy("register", registerRateLimiter, ipKeyResolver);
        }
        if ("/auth/logout".equals(path) && method == HttpMethod.POST) {
            return new Policy("auth-logout", authLogoutRateLimiter, userOrIpKeyResolver);
        }
        if ("/auth/resend-verification".equals(path) && method == HttpMethod.POST) {
            return new Policy("auth-resend-verification", authResendVerificationRateLimiter, userOrIpKeyResolver);
        }
        if ("/customers/me".equals(path) && (method == HttpMethod.GET || method == HttpMethod.PUT)) {
            return new Policy("customer-me", customerMeRateLimiter, userOrIpKeyResolver);
        }
        if (("/customers/me/addresses".equals(path) || path.startsWith("/customers/me/addresses/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("customer-addresses-read", customerAddressesRateLimiter, userOrIpKeyResolver);
        }
        if (("/customers/me/addresses".equals(path) || path.startsWith("/customers/me/addresses/"))
                && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
            return new Policy("customer-addresses-write", customerAddressesWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/orders/me".equals(path) && method == HttpMethod.POST) {
            return new Policy("orders-me-write", ordersMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (path.startsWith("/orders/me/") && path.endsWith("/cancel") && method == HttpMethod.POST) {
            return new Policy("orders-me-write", ordersMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (("/orders/me".equals(path) || path.startsWith("/orders/me/")) && method == HttpMethod.GET) {
            return new Policy("orders-me-read", ordersMeRateLimiter, userOrIpKeyResolver);
        }
        if ("/cart/me/checkout".equals(path) && method == HttpMethod.POST) {
            return new Policy("cart-me-checkout", cartMeCheckoutRateLimiter, userOrIpKeyResolver);
        }
        if (("/cart/me".equals(path) || "/cart/me/items".equals(path) || path.startsWith("/cart/me/items/"))
                && (method == HttpMethod.DELETE || method == HttpMethod.PUT || method == HttpMethod.POST)) {
            return new Policy("cart-me-write", cartMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (("/cart/me".equals(path) || "/cart/me/items".equals(path) || path.startsWith("/cart/me/items/"))
                && method == HttpMethod.GET) {
            return new Policy("cart-me-read", cartMeRateLimiter, userOrIpKeyResolver);
        }
        if (("/wishlist/me".equals(path) || "/wishlist/me/items".equals(path) || path.startsWith("/wishlist/me/items/"))
                && (method == HttpMethod.DELETE || method == HttpMethod.PUT || method == HttpMethod.POST)) {
            return new Policy("wishlist-me-write", wishlistMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (("/wishlist/me".equals(path) || "/wishlist/me/items".equals(path) || path.startsWith("/wishlist/me/items/"))
                && method == HttpMethod.GET) {
            return new Policy("wishlist-me-read", wishlistMeRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/orders".equals(path) || path.startsWith("/admin/orders/")) {
            return new Policy("admin-orders", adminOrdersRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/vendor-orders".equals(path) || path.startsWith("/admin/vendor-orders/")) {
            return new Policy("admin-orders", adminOrdersRateLimiter, userOrIpKeyResolver);
        }
        if ("/promotions/me".equals(path) || path.startsWith("/promotions/me/")) {
            return new Policy("promotions-me", publicPromotionsRateLimiter, userOrIpKeyResolver);
        }
        if (("/promotions".equals(path) || path.startsWith("/promotions/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("promotions-read", publicPromotionsRateLimiter, ipKeyResolver);
        }
        if (("/products".equals(path) || path.startsWith("/products/")
                || "/categories".equals(path) || path.startsWith("/categories/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("products-read", productsRateLimiter, ipKeyResolver);
        }
        if ("/vendors/me".equals(path) || path.startsWith("/vendors/me/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("vendor-me-read", vendorMeRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("vendor-me-write", vendorMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (("/posters".equals(path) || path.startsWith("/posters/")
                || "/vendors".equals(path) || path.startsWith("/vendors/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("catalog-aux-read", publicCatalogAuxRateLimiter, ipKeyResolver);
        }
        if ("/admin/products".equals(path) || path.startsWith("/admin/products/")
                || "/admin/categories".equals(path) || path.startsWith("/admin/categories/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("admin-products-read", adminProductsRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("admin-products-write", adminProductsWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/posters".equals(path) || path.startsWith("/admin/posters/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("admin-posters-read", adminPostersRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("admin-posters-write", adminPostersWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/vendors".equals(path) || path.startsWith("/admin/vendors/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("admin-vendors-read", adminVendorsRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("admin-vendors-write", adminVendorsWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/me".equals(path) || path.startsWith("/admin/me/")) {
            return new Policy("admin-me-read", adminMeRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/keycloak/users/search".equals(path) && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("admin-keycloak-search", adminKeycloakSearchRateLimiter, userOrIpKeyResolver);
        }
        if ("/admin/platform-staff".equals(path) || path.startsWith("/admin/platform-staff/")
                || "/admin/vendor-staff".equals(path) || path.startsWith("/admin/vendor-staff/")
                || "/admin/access-audit".equals(path) || path.startsWith("/admin/access-audit/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("admin-access-read", adminAccessRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("admin-access-write", adminAccessWriteRateLimiter, userOrIpKeyResolver);
        }
        if (path.startsWith("/webhooks/")) {
            return new Policy("webhooks", webhookRateLimiter, ipKeyResolver);
        }
        if (("/payments/me".equals(path) || path.startsWith("/payments/me/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("payment-me-read", paymentMeRateLimiter, userOrIpKeyResolver);
        }
        if (("/payments/me".equals(path) || path.startsWith("/payments/me/"))
                && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
            return new Policy("payment-me-write", paymentMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/payments/vendor/me".equals(path) || path.startsWith("/payments/vendor/me/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("payment-vendor-me-read", paymentMeRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("payment-vendor-me-write", paymentMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if ("/search".equals(path) || path.startsWith("/search/")) {
            return new Policy("search-read", productsRateLimiter, ipKeyResolver);
        }
        if (("/reviews".equals(path) || path.startsWith("/reviews/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("reviews-read", publicCatalogAuxRateLimiter, ipKeyResolver);
        }
        return new Policy("default", gatewayDefaultRateLimiter, userOrIpKeyResolver);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private byte[] toJsonBytes(Map<String, Object> body, int fallbackStatus) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            return ("{\"status\":" + fallbackStatus + ",\"error\":\"Internal Error\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private record Policy(String id, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
    }

    @NullUnmarked
    private record RateLimitDecision(RateLimiter.Response response, Throwable error) {
        private static RateLimitDecision allowed(RateLimiter.Response response) {
            return new RateLimitDecision(response, null);
        }

        private static RateLimitDecision error(Throwable error) {
            return new RateLimitDecision(null, error);
        }
    }
}
