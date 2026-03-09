package com.rumal.payment_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PaymentMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public PaymentMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${payment.idempotency.enabled:true}") boolean enabled,
            @Value("${payment.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${payment.idempotency.response-ttl:24h}") Duration responseTtl,
            @Value("${payment.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${payment.idempotency.key-prefix:pay:idem:v1::}") String keyPrefix
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
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        if ("/payments/me/initiate".equals(path)) {
            return true;
        }
        if ("/payments/me/refunds".equals(path)) {
            return true;
        }
        if (path.startsWith("/payments/vendor/me/refunds/") && path.endsWith("/respond")) {
            return true;
        }
        return path.startsWith("/admin/payments/refunds/") && path.endsWith("/finalize");
    }
}
