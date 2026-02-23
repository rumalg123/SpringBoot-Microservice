package com.rumal.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class OrderMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public OrderMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${order.idempotency.enabled:true}") boolean enabled,
            @Value("${order.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${order.idempotency.response-ttl:24h}") Duration responseTtl,
            @Value("${order.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${order.idempotency.key-prefix:os:idem:v1::}") String keyPrefix
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
        return "POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    @Override
    protected boolean isProtectedMutationPath(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            return "/orders".equals(path) || "/orders/me".equals(path);
        }
        if (!"PATCH".equalsIgnoreCase(method)) {
            return false;
        }
        return (path.startsWith("/orders/") && path.endsWith("/status"))
                || (path.startsWith("/orders/vendor-orders/") && path.endsWith("/status"));
    }

    @Override
    protected String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }
}
