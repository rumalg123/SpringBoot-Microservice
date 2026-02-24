package com.rumal.poster_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PosterMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public PosterMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${poster.idempotency.enabled:true}") boolean enabled,
            @Value("${poster.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${poster.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${poster.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${poster.idempotency.key-prefix:pos:idem:v1::}") String keyPrefix
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
            return "/admin/posters".equals(path)
                    || path.startsWith("/admin/posters/") && path.endsWith("/restore")
                    || path.startsWith("/admin/posters/") && path.endsWith("/variants");
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.startsWith("/admin/posters/");
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return path.startsWith("/admin/posters/");
        }
        return false;
    }
}
