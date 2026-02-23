package com.rumal.promotion_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.shared.idempotency.servlet.AbstractRedisServletIdempotencyFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class PromotionMutationIdempotencyFilter extends AbstractRedisServletIdempotencyFilter {
    private final boolean enabled;

    public PromotionMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${promotion.idempotency.enabled:true}") boolean enabled,
            @Value("${promotion.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${promotion.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${promotion.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${promotion.idempotency.key-prefix:promo:idem:v1::}") String keyPrefix
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
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    @Override
    protected boolean isProtectedMutationPath(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            // POST /admin/promotions (create)
            if ("/admin/promotions".equals(path)) {
                return true;
            }
            // POST /admin/promotions/{id}/submit
            // POST /admin/promotions/{id}/approve
            // POST /admin/promotions/{id}/reject
            // POST /admin/promotions/{id}/activate
            // POST /admin/promotions/{id}/pause
            // POST /admin/promotions/{id}/archive
            if (path.startsWith("/admin/promotions/") && (
                    path.endsWith("/submit")
                            || path.endsWith("/approve")
                            || path.endsWith("/reject")
                            || path.endsWith("/activate")
                            || path.endsWith("/pause")
                            || path.endsWith("/archive")
            )) {
                return true;
            }
            // POST /admin/promotions/{id}/coupons (create coupon)
            if (path.startsWith("/admin/promotions/") && path.endsWith("/coupons")) {
                return true;
            }
            // POST /internal/promotions/reservations (reserve)
            if ("/internal/promotions/reservations".equals(path)) {
                return true;
            }
            // POST /internal/promotions/reservations/{id}/commit
            // POST /internal/promotions/reservations/{id}/release
            if (path.startsWith("/internal/promotions/reservations/") && (
                    path.endsWith("/commit") || path.endsWith("/release")
            )) {
                return true;
            }
            return false;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            // PUT /admin/promotions/{id} (update)
            return path.startsWith("/admin/promotions/") && !path.substring("/admin/promotions/".length()).contains("/");
        }
        return false;
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
