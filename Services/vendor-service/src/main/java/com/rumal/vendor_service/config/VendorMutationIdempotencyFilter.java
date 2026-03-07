package com.rumal.vendor_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class VendorMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public VendorMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${vendor.idempotency.enabled:true}") boolean enabled,
            @Value("${vendor.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${vendor.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${vendor.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${vendor.idempotency.key-prefix:vs:idem:v1::}") String keyPrefix
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
            return path.matches("^/admin/vendors/[^/]+/users$");
        }
        if ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return path.matches("^/admin/vendors/[^/]+/users/[^/]+$");
        }
        return false;
    }
}
