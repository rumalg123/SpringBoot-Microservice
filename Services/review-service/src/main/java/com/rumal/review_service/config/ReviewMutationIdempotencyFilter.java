package com.rumal.review_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReviewMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {

    private final boolean enabled;

    public ReviewMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${review.idempotency.enabled:true}") boolean enabled,
            @Value("${review.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${review.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${review.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${review.idempotency.key-prefix:rs:idem:v1::}") String keyPrefix
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
            return "/reviews/me".equals(path)
                    || "/reviews/me/images".equals(path)
                    || (path.startsWith("/reviews/me/") && path.endsWith("/vote"))
                    || (path.startsWith("/reviews/me/") && path.endsWith("/report"))
                    || (path.startsWith("/reviews/vendor/") && path.endsWith("/reply"));
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.startsWith("/reviews/me/")
                    || path.startsWith("/reviews/vendor/replies/");
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return path.startsWith("/reviews/me/")
                    || path.startsWith("/reviews/vendor/replies/")
                    || path.startsWith("/admin/reviews/");
        }
        return false;
    }
}
