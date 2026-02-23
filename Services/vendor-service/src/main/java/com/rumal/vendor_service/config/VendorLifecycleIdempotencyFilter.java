package com.rumal.vendor_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class VendorLifecycleIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public VendorLifecycleIdempotencyFilter(
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
    protected boolean supportsMethod(String method) {
        return "POST".equalsIgnoreCase(method);
    }

    @Override
    protected boolean isProtectedMutationPath(String path, String method) {
        if (!path.startsWith("/admin/vendors/")) {
            return false;
        }
        return path.endsWith("/delete-request")
                || path.endsWith("/confirm-delete")
                || path.endsWith("/stop-orders")
                || path.endsWith("/resume-orders")
                || path.endsWith("/restore");
    }

    @Override
    protected String anonymousActorScope() {
        return "internal";
    }
}
