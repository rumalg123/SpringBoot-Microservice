package com.rumal.cart_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CartMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public CartMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${cart.idempotency.enabled:true}") boolean enabled,
            @Value("${cart.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${cart.idempotency.response-ttl:12h}") Duration responseTtl,
            @Value("${cart.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${cart.idempotency.key-prefix:cs:idem:v1::}") String keyPrefix
    ) {
        super(redisTemplate, objectMapper, pendingTtl, responseTtl, keyHeaderName, keyPrefix);
        this.enabled = enabled;
    }

    @Override
    protected boolean isFilterEnabled() {
        return enabled;
    }

    @Override
    protected boolean supportsMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    @Override
    protected boolean isProtectedMutationPath(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            return "/cart/me/checkout".equals(path) || "/cart/me/items".equals(path);
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.startsWith("/cart/me/items/");
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return "/cart/me".equals(path) || path.startsWith("/cart/me/items/");
        }
        return false;
    }
}
