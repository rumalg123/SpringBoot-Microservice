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

    private final RedisRateLimiter registerIdentityRateLimiter;
    private final RedisRateLimiter authLogoutRateLimiter;
    private final RedisRateLimiter authResendVerificationRateLimiter;
    private final RedisRateLimiter authSessionRateLimiter;
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
    private final RedisRateLimiter analyticsAdminRateLimiter;
    private final RedisRateLimiter analyticsVendorRateLimiter;
    private final RedisRateLimiter reportExportsCreateRateLimiter;
    private final RedisRateLimiter reportExportsReadRateLimiter;
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
    private final RedisRateLimiter personalizationEventsRateLimiter;
    private final RedisRateLimiter personalizationReadRateLimiter;
    private final RedisRateLimiter webhookRateLimiter;
    private final RedisRateLimiter gatewayDefaultRateLimiter;
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userOrIpKeyResolver;
    private final ObjectMapper objectMapper;
    private final boolean failOpenOnRateLimiterError;

    public RateLimitEnforcementFilter(
            @Qualifier("registerIdentityRateLimiter") RedisRateLimiter registerIdentityRateLimiter,
            @Qualifier("authLogoutRateLimiter") RedisRateLimiter authLogoutRateLimiter,
            @Qualifier("authResendVerificationRateLimiter") RedisRateLimiter authResendVerificationRateLimiter,
            @Qualifier("authSessionRateLimiter") RedisRateLimiter authSessionRateLimiter,
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
            @Qualifier("analyticsAdminRateLimiter") RedisRateLimiter analyticsAdminRateLimiter,
            @Qualifier("analyticsVendorRateLimiter") RedisRateLimiter analyticsVendorRateLimiter,
            @Qualifier("reportExportsCreateRateLimiter") RedisRateLimiter reportExportsCreateRateLimiter,
            @Qualifier("reportExportsReadRateLimiter") RedisRateLimiter reportExportsReadRateLimiter,
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
            @Qualifier("personalizationEventsRateLimiter") RedisRateLimiter personalizationEventsRateLimiter,
            @Qualifier("personalizationReadRateLimiter") RedisRateLimiter personalizationReadRateLimiter,
            @Qualifier("webhookRateLimiter") RedisRateLimiter webhookRateLimiter,
            @Qualifier("gatewayDefaultRateLimiter") RedisRateLimiter gatewayDefaultRateLimiter,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver,
            ObjectMapper objectMapper,
            @Value("${rate-limit.fail-open-on-error:true}") boolean failOpenOnRateLimiterError
    ) {
        this.registerIdentityRateLimiter = registerIdentityRateLimiter;
        this.authLogoutRateLimiter = authLogoutRateLimiter;
        this.authResendVerificationRateLimiter = authResendVerificationRateLimiter;
        this.authSessionRateLimiter = authSessionRateLimiter;
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
        this.analyticsAdminRateLimiter = analyticsAdminRateLimiter;
        this.analyticsVendorRateLimiter = analyticsVendorRateLimiter;
        this.reportExportsCreateRateLimiter = reportExportsCreateRateLimiter;
        this.reportExportsReadRateLimiter = reportExportsReadRateLimiter;
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
        this.personalizationEventsRateLimiter = personalizationEventsRateLimiter;
        this.personalizationReadRateLimiter = personalizationReadRateLimiter;
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
                policyId, path, requestId, shouldFailOpen(path), error);

        exchange.getResponse().getHeaders().set("X-RateLimit-Policy", policyId);
        exchange.getResponse().getHeaders().set("X-RateLimit-Status", "ERROR");

        // H-01: Critical endpoints fail CLOSED even when global setting is fail-open
        if (shouldFailOpen(path)) {
            return chain.filter(exchange);
        }
        return writeRateLimitServiceUnavailable(exchange, policyId);
    }

    /**
     * H-01: Determines whether to fail open or closed when the rate limiter is unavailable.
     * Critical financial, auth, and registration endpoints always fail CLOSED to prevent abuse.
     */
    private boolean shouldFailOpen(String path) {
        return failOpenOnRateLimiterError && !isFailClosedPath(path);
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
        Policy policy = resolveAuthPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveCustomerPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveOrderPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveCartAndWishlistPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveAnalyticsAndReportPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolvePromotionAndCatalogPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveVendorPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveAdminCatalogPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveAdminAccessPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveWebhookPaymentAndPersonalizationPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        policy = resolveSearchAndReviewPolicy(path, method);
        if (policy != null) {
            return policy;
        }
        return policy("default", gatewayDefaultRateLimiter, userOrIpKeyResolver);
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

    private boolean isFailClosedPath(String path) {
        return path.startsWith("/orders")
                || path.startsWith("/cart/me/checkout")
                || path.startsWith("/payments")
                || path.startsWith("/webhooks")
                || "/customers/register-identity".equals(path)
                || path.startsWith("/auth")
                || path.startsWith("/personalization/events")
                || path.startsWith("/analytics/")
                || path.startsWith("/admin/orders/export");
    }

    private @Nullable Policy resolveAuthPolicy(String path, @Nullable HttpMethod method) {
        if (matches(method, HttpMethod.POST, "/customers/register-identity", path)) {
            return policy("register-identity", registerIdentityRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.POST, "/auth/logout", path)) {
            return policy("auth-logout", authLogoutRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.POST, "/auth/resend-verification", path)) {
            return policy("auth-resend-verification", authResendVerificationRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.POST, "/auth/session", path)) {
            return policy("auth-session", authSessionRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveCustomerPolicy(String path, @Nullable HttpMethod method) {
        if ("/customers/me".equals(path) && (isReadMethod(method) || method == HttpMethod.PUT)) {
            return policy("customer-me", customerMeRateLimiter, userOrIpKeyResolver);
        }
        if (matchesAddressPath(path) && isReadMethod(method)) {
            return policy("customer-addresses-read", customerAddressesRateLimiter, userOrIpKeyResolver);
        }
        if (matchesAddressPath(path) && isWriteMethod(method)) {
            return policy("customer-addresses-write", customerAddressesWriteRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveOrderPolicy(String path, @Nullable HttpMethod method) {
        if (matches(method, HttpMethod.POST, "/orders/me", path)
                || (method == HttpMethod.POST && path.startsWith("/orders/me/") && path.endsWith("/cancel"))) {
            return policy("orders-me-write", ordersMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (matchesPrefix(path, "/orders/me") && method == HttpMethod.GET) {
            return policy("orders-me-read", ordersMeRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveCartAndWishlistPolicy(String path, @Nullable HttpMethod method) {
        if (matches(method, HttpMethod.POST, "/cart/me/checkout", path)) {
            return policy("cart-me-checkout", cartMeCheckoutRateLimiter, userOrIpKeyResolver);
        }
        if (matchesCartPath(path) && isWriteMethod(method)) {
            return policy("cart-me-write", cartMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (matchesCartPath(path) && method == HttpMethod.GET) {
            return policy("cart-me-read", cartMeRateLimiter, userOrIpKeyResolver);
        }
        if (matchesWishlistPath(path) && isWriteMethod(method)) {
            return policy("wishlist-me-write", wishlistMeWriteRateLimiter, userOrIpKeyResolver);
        }
        if (matchesWishlistPath(path) && method == HttpMethod.GET) {
            return policy("wishlist-me-read", wishlistMeRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveAnalyticsAndReportPolicy(String path, @Nullable HttpMethod method) {
        if (path.startsWith("/analytics/admin/")) {
            return policy("analytics-admin", analyticsAdminRateLimiter, userOrIpKeyResolver);
        }
        if (path.startsWith("/analytics/vendor/")) {
            return policy("analytics-vendor", analyticsVendorRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.GET, "/admin/orders/export", path)
                || matches(method, HttpMethod.POST, "/admin/orders/exports", path)) {
            return policy("report-exports-create", reportExportsCreateRateLimiter, userOrIpKeyResolver);
        }
        if (path.startsWith("/admin/orders/exports/") && isReadMethod(method)) {
            return policy("report-exports-read", reportExportsReadRateLimiter, userOrIpKeyResolver);
        }
        if (matchesPrefix(path, "/admin/orders") || matchesPrefix(path, "/admin/vendor-orders")) {
            return policy("admin-orders", adminOrdersRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolvePromotionAndCatalogPolicy(String path, @Nullable HttpMethod method) {
        if (matchesPrefix(path, "/promotions/me")) {
            return policy("promotions-me", publicPromotionsRateLimiter, userOrIpKeyResolver);
        }
        if (matchesPrefix(path, "/promotions") && isReadMethod(method)) {
            return policy("promotions-read", publicPromotionsRateLimiter, ipKeyResolver);
        }
        if ((matchesPrefix(path, "/products") || matchesPrefix(path, "/categories")) && isReadMethod(method)) {
            return policy("products-read", productsRateLimiter, ipKeyResolver);
        }
        if ((matchesPrefix(path, "/posters") || matchesPrefix(path, "/vendors")) && isReadMethod(method)) {
            return policy("catalog-aux-read", publicCatalogAuxRateLimiter, ipKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveVendorPolicy(String path, @Nullable HttpMethod method) {
        if (matchesPrefix(path, "/vendors/me")) {
            return readWritePolicy(
                    method,
                    "vendor-me-read",
                    vendorMeRateLimiter,
                    "vendor-me-write",
                    vendorMeWriteRateLimiter
            );
        }
        return null;
    }

    private @Nullable Policy resolveAdminCatalogPolicy(String path, @Nullable HttpMethod method) {
        if (matchesPrefix(path, "/admin/products") || matchesPrefix(path, "/admin/categories")) {
            return readWritePolicy(
                    method,
                    "admin-products-read",
                    adminProductsRateLimiter,
                    "admin-products-write",
                    adminProductsWriteRateLimiter
            );
        }
        if (matchesPrefix(path, "/admin/posters")) {
            return readWritePolicy(
                    method,
                    "admin-posters-read",
                    adminPostersRateLimiter,
                    "admin-posters-write",
                    adminPostersWriteRateLimiter
            );
        }
        if (matchesPrefix(path, "/admin/vendors")) {
            return readWritePolicy(
                    method,
                    "admin-vendors-read",
                    adminVendorsRateLimiter,
                    "admin-vendors-write",
                    adminVendorsWriteRateLimiter
            );
        }
        if (matchesPrefix(path, "/admin/me")) {
            return policy("admin-me-read", adminMeRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.GET, "/admin/keycloak/users/search", path)
                || matches(method, HttpMethod.HEAD, "/admin/keycloak/users/search", path)) {
            return policy("admin-keycloak-search", adminKeycloakSearchRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveAdminAccessPolicy(String path, @Nullable HttpMethod method) {
        if (matchesPrefix(path, "/admin/platform-staff")
                || matchesPrefix(path, "/admin/vendor-staff")
                || matchesPrefix(path, "/admin/access-audit")) {
            return readWritePolicy(
                    method,
                    "admin-access-read",
                    adminAccessRateLimiter,
                    "admin-access-write",
                    adminAccessWriteRateLimiter
            );
        }
        return null;
    }

    private @Nullable Policy resolveWebhookPaymentAndPersonalizationPolicy(String path, @Nullable HttpMethod method) {
        if (path.startsWith("/webhooks/")) {
            return policy("webhooks", webhookRateLimiter, ipKeyResolver);
        }
        if (matchesPrefix(path, "/payments/me")) {
            return readWritePolicy(
                    method,
                    "payment-me-read",
                    paymentMeRateLimiter,
                    "payment-me-write",
                    paymentMeWriteRateLimiter
            );
        }
        if (matchesPrefix(path, "/payments/vendor/me")) {
            return readWritePolicy(
                    method,
                    "payment-vendor-me-read",
                    paymentMeRateLimiter,
                    "payment-vendor-me-write",
                    paymentMeWriteRateLimiter
            );
        }
        if (matches(method, HttpMethod.POST, "/personalization/events", path)) {
            return policy("personalization-events", personalizationEventsRateLimiter, userOrIpKeyResolver);
        }
        if ((matchesPrefix(path, "/personalization/me")
                || matchesPrefix(path, "/personalization/trending")
                || path.startsWith("/personalization/products/")) && isReadMethod(method)) {
            return policy("personalization-read", personalizationReadRateLimiter, userOrIpKeyResolver);
        }
        if (matches(method, HttpMethod.POST, "/personalization/sessions/merge", path)) {
            return policy("personalization-read", personalizationReadRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    private @Nullable Policy resolveSearchAndReviewPolicy(String path, @Nullable HttpMethod method) {
        if (matchesPrefix(path, "/search")) {
            return policy("search-read", productsRateLimiter, ipKeyResolver);
        }
        if (matchesPrefix(path, "/reviews") && isReadMethod(method)) {
            return policy("reviews-read", publicCatalogAuxRateLimiter, ipKeyResolver);
        }
        return null;
    }

    private Policy readWritePolicy(
            @Nullable HttpMethod method,
            String readPolicyId,
            RedisRateLimiter readRateLimiter,
            String writePolicyId,
            RedisRateLimiter writeRateLimiter
    ) {
        return isReadMethod(method)
                ? policy(readPolicyId, readRateLimiter, userOrIpKeyResolver)
                : policy(writePolicyId, writeRateLimiter, userOrIpKeyResolver);
    }

    private Policy policy(String id, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        return new Policy(id, rateLimiter, keyResolver);
    }

    private boolean matches(@Nullable HttpMethod actualMethod, HttpMethod expectedMethod, String expectedPath, String actualPath) {
        return actualMethod == expectedMethod && expectedPath.equals(actualPath);
    }

    private boolean matchesPrefix(String path, String prefix) {
        return prefix.equals(path) || path.startsWith(prefix + "/");
    }

    private boolean matchesAddressPath(String path) {
        return "/customers/me/addresses".equals(path) || path.startsWith("/customers/me/addresses/");
    }

    private boolean matchesCartPath(String path) {
        return "/cart/me".equals(path) || "/cart/me/items".equals(path) || path.startsWith("/cart/me/items/");
    }

    private boolean matchesWishlistPath(String path) {
        return "/wishlist/me".equals(path) || "/wishlist/me/items".equals(path) || path.startsWith("/wishlist/me/items/");
    }

    private boolean isReadMethod(@Nullable HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD;
    }

    private boolean isWriteMethod(@Nullable HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE;
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
