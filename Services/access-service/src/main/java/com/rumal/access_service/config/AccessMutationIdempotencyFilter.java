package com.rumal.access_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AccessMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public AccessMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${access.idempotency.enabled:true}") boolean enabled,
            @Value("${access.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${access.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${access.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${access.idempotency.key-prefix:access:idem:v1::}") String keyPrefix
    ) {
        super(redisTemplate, objectMapper, pendingTtl, responseTtl, keyHeaderName, keyPrefix);
        this.enabled = enabled;
    }

    @Override
    protected boolean isFilterEnabled() {
        return enabled;
    }

    @Override
    protected boolean isProtectedMutationPath(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            return path.matches("^/admin/platform-staff$")
                    || path.matches("^/admin/platform-staff/[^/]+/restore$")
                    || path.matches("^/admin/vendor-staff$")
                    || path.matches("^/admin/vendor-staff/[^/]+/restore$");
        }
        if ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return path.matches("^/admin/platform-staff/[^/]+$")
                    || path.matches("^/admin/vendor-staff/[^/]+$");
        }
        return false;
    }
}
