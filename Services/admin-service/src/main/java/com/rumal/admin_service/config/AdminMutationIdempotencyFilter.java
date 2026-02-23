package com.rumal.admin_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class AdminMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public AdminMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${admin.idempotency.enabled:true}") boolean enabled,
            @Value("${admin.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${admin.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${admin.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${admin.idempotency.key-prefix:admin:idem:v1::}") String keyPrefix
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
        if (!StringUtils.hasText(path) || !path.startsWith("/admin/")) {
            return false;
        }
        if ("PATCH".equalsIgnoreCase(method)) {
            return path.matches("^/admin/orders/[^/]+/status$")
                    || path.matches("^/admin/orders/vendor-orders/[^/]+/status$");
        }
        if ("POST".equalsIgnoreCase(method)) {
            return path.matches("^/admin/vendors/[^/]+/(stop-orders|resume-orders|delete-request|confirm-delete|restore)$")
                    || path.matches("^/admin/vendors/[^/]+/users/onboard$")
                    || path.matches("^/admin/vendors/[^/]+/users$")
                    || path.matches("^/admin/posters(/.*)?$")
                    || path.matches("^/admin/platform-staff(/.*)?$")
                    || path.matches("^/admin/vendor-staff(/.*)?$");
        }
        if ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            if (path.matches("^/admin/vendors/[^/]+/users/[^/]+$")) {
                return true;
            }
            return path.matches("^/admin/posters(/.*)?$")
                    || path.matches("^/admin/platform-staff(/.*)?$")
                    || path.matches("^/admin/vendor-staff(/.*)?$");
        }
        return false;
    }
}
