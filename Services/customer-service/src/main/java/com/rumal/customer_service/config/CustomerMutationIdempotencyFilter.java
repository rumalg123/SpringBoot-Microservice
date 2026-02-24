package com.rumal.customer_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CustomerMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public CustomerMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${customer.idempotency.enabled:true}") boolean enabled,
            @Value("${customer.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${customer.idempotency.response-ttl:24h}") Duration responseTtl,
            @Value("${customer.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${customer.idempotency.key-prefix:cust:idem:v1::}") String keyPrefix
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
            return "/customers/register".equals(path)
                    || "/customers/register-identity".equals(path)
                    || "/customers/me/addresses".equals(path)
                    || "/customers/me/deactivate".equals(path)
                    || path.matches("/customers/me/addresses/[^/]+/default-shipping")
                    || path.matches("/customers/me/addresses/[^/]+/default-billing")
                    || path.matches("/customers/internal/[^/]+/add-loyalty-points");
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return "/customers/me".equals(path)
                    || path.matches("/customers/me/addresses/[^/]+")
                    || "/customers/me/communication-preferences".equals(path);
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return path.matches("/customers/me/addresses/[^/]+");
        }
        return false;
    }
}
