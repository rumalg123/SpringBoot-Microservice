package com.rumal.wishlist_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WishlistMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public WishlistMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${wishlist.idempotency.enabled:true}") boolean enabled,
            @Value("${wishlist.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${wishlist.idempotency.response-ttl:12h}") Duration responseTtl,
            @Value("${wishlist.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${wishlist.idempotency.key-prefix:ws:idem:v1::}") String keyPrefix
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
            return "/wishlist/me/items".equals(path)
                    || path.startsWith("/wishlist/me/items/") && path.endsWith("/move-to-cart")
                    || "/wishlist/me/collections".equals(path)
                    || path.startsWith("/wishlist/me/collections/") && path.endsWith("/share");
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.startsWith("/wishlist/me/items/")
                    || path.startsWith("/wishlist/me/collections/");
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return path.startsWith("/wishlist/me/items/")
                    || path.startsWith("/wishlist/me/collections/")
                    || "/wishlist/me".equals(path);
        }
        return false;
    }
}
