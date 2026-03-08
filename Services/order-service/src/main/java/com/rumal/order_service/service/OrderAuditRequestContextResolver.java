package com.rumal.order_service.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OrderAuditRequestContextResolver {

    public OrderAuditRequestContext resolve(
            String actorSubOverride,
            String actorRolesOverride,
            String actorTypeOverride,
            String changeSourceOverride
    ) {
        HttpServletRequest request = currentRequest();
        String actorSub = firstNonBlank(actorSubOverride, header(request, "X-User-Sub"), "system");
        String actorTenantId = trimToNull(firstNonBlank(header(request, "X-Actor-Tenant-Id"), header(request, "X-Caller-Vendor-Id")));
        String actorRoles = trimToNull(firstNonBlank(actorRolesOverride, header(request, "X-User-Roles")));
        String actorType = StringUtils.hasText(actorTypeOverride)
                ? actorTypeOverride.trim()
                : (StringUtils.hasText(actorSub) && !"system".equalsIgnoreCase(actorSub) ? "USER" : "SYSTEM");
        String changeSource = StringUtils.hasText(changeSourceOverride)
                ? changeSourceOverride.trim()
                : (request != null ? "API" : "SYSTEM");
        String clientIp = normalizeIp(firstNonBlank(
                header(request, "X-Audit-Client-Ip"),
                header(request, "X-Forwarded-For"),
                request == null ? null : request.getRemoteAddr()
        ));
        String userAgent = truncate(trimToNull(firstNonBlank(header(request, "X-Audit-User-Agent"), header(request, "User-Agent"))), 512);
        String requestId = truncate(trimToNull(header(request, "X-Request-Id")), 100);
        return new OrderAuditRequestContext(actorSub, actorTenantId, actorRoles, actorType, changeSource, clientIp, userAgent, requestId);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        return request.getHeader(name);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        int commaIndex = normalized.indexOf(',');
        if (commaIndex >= 0) {
            normalized = normalized.substring(0, commaIndex).trim();
        }
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        return truncate(normalized, 45);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[\\r\\n]+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
