package com.rumal.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class RateLimitEnforcementFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter registerRateLimiter;
    private final RedisRateLimiter customerMeRateLimiter;
    private final RedisRateLimiter ordersMeRateLimiter;
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userOrIpKeyResolver;

    public RateLimitEnforcementFilter(
            RedisRateLimiter registerRateLimiter,
            RedisRateLimiter customerMeRateLimiter,
            RedisRateLimiter ordersMeRateLimiter,
            KeyResolver ipKeyResolver,
            KeyResolver userOrIpKeyResolver
    ) {
        this.registerRateLimiter = registerRateLimiter;
        this.customerMeRateLimiter = customerMeRateLimiter;
        this.ordersMeRateLimiter = ordersMeRateLimiter;
        this.ipKeyResolver = ipKeyResolver;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        Policy policy = resolvePolicy(path);
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

    private Policy resolvePolicy(String path) {
        if ("/customers/register".equals(path) || "/customers/register-auth0".equals(path)) {
            return new Policy("register", registerRateLimiter, ipKeyResolver);
        }
        if ("/customers/me".equals(path)) {
            return new Policy("customer-me", customerMeRateLimiter, userOrIpKeyResolver);
        }
        if ("/orders/me".equals(path) || path.startsWith("/orders/me/")) {
            return new Policy("orders-me", ordersMeRateLimiter, userOrIpKeyResolver);
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
