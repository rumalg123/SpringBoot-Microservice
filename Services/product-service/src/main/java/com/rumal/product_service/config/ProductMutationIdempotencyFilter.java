package com.rumal.product_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProductMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public ProductMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${product.idempotency.enabled:true}") boolean enabled,
            @Value("${product.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${product.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${product.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${product.idempotency.key-prefix:ps:idem:v1::}") String keyPrefix
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
            return "/admin/products".equals(path)
                    || path.startsWith("/admin/products/") && path.endsWith("/variations")
                    || path.startsWith("/admin/products/") && path.endsWith("/restore")
                    || path.startsWith("/admin/products/") && path.endsWith("/submit-for-review")
                    || path.startsWith("/admin/products/") && path.endsWith("/approve")
                    || path.startsWith("/admin/products/") && path.endsWith("/reject")
                    || "/admin/products/bulk-delete".equals(path)
                    || "/admin/products/bulk-price-update".equals(path)
                    || "/admin/products/bulk-category-reassign".equals(path)
                    || "/admin/categories".equals(path)
                    || path.startsWith("/admin/categories/") && path.endsWith("/restore");
        }
        if ("PUT".equalsIgnoreCase(method)) {
            return path.startsWith("/admin/products/")
                    || path.startsWith("/admin/categories/");
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return path.startsWith("/admin/products/")
                    || path.startsWith("/admin/categories/");
        }
        return false;
    }
}
