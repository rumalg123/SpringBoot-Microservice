package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@NullMarked
public class RateLimitEnforcementFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter registerRateLimiter;
    private final RedisRateLimiter customerMeRateLimiter;
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
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userOrIpKeyResolver;

    public RateLimitEnforcementFilter(
            @Qualifier("registerRateLimiter") RedisRateLimiter registerRateLimiter,
            @Qualifier("customerMeRateLimiter") RedisRateLimiter customerMeRateLimiter,
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
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver
    ) {
        this.registerRateLimiter = registerRateLimiter;
        this.customerMeRateLimiter = customerMeRateLimiter;
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
        this.ipKeyResolver = ipKeyResolver;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        Policy policy = resolvePolicy(path, method);
        if (policy == null) {
            return chain.filter(exchange);
        }

        return policy.keyResolver().resolve(exchange)
                .defaultIfEmpty("ip:unknown")
                .flatMap(key -> policy.rateLimiter().isAllowed(policy.id(), key))
                .flatMap(result -> {
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
        String body = "{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"path\":\"" + exchange.getRequest().getPath().value() + "\"," +
                "\"status\":429," +
                "\"error\":\"Too Many Requests\"," +
                "\"message\":\"Rate limit exceeded\"," +
                "\"policyId\":\"" + policyId + "\"," +
                "\"requestId\":\"" + (requestId == null ? "" : requestId) + "\"}";

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "1");
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private @Nullable Policy resolvePolicy(String path, @Nullable HttpMethod method) {
        if (("/customers/register".equals(path) || "/customers/register-identity".equals(path))
                && method == HttpMethod.POST) {
            return new Policy("register", registerRateLimiter, ipKeyResolver);
        }
        if ("/customers/me".equals(path) && (method == HttpMethod.GET || method == HttpMethod.PUT)) {
            return new Policy("customer-me", customerMeRateLimiter, userOrIpKeyResolver);
        }
        if ("/orders/me".equals(path) && method == HttpMethod.POST) {
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
        if (("/products".equals(path) || path.startsWith("/products/")
                || "/categories".equals(path) || path.startsWith("/categories/"))
                && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            return new Policy("products-read", productsRateLimiter, ipKeyResolver);
        }
        if ("/admin/products".equals(path) || path.startsWith("/admin/products/")
                || "/admin/categories".equals(path) || path.startsWith("/admin/categories/")) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                return new Policy("admin-products-read", adminProductsRateLimiter, userOrIpKeyResolver);
            }
            return new Policy("admin-products-write", adminProductsWriteRateLimiter, userOrIpKeyResolver);
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private record Policy(String id, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
    }
}
