package com.rumal.access_service.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AccessAuditRequestContextResolver {

    private static final String ACTOR_TYPE_USER = "USER";
    private static final String SYSTEM_SOURCE = "SYSTEM";
    private static final String ADMIN_API_SOURCE = "ADMIN_API";

    public AccessAuditRequestContext resolve(String actorSubOverride, String actorRolesOverride, String changeSourceOverride) {
        HttpServletRequest request = currentRequest();
        String actorSub = firstNonBlank(actorSubOverride, header(request, "X-User-Sub"));
        String actorTenantId = trimToNull(firstNonBlank(header(request, "X-Actor-Tenant-Id"), header(request, "X-Caller-Vendor-Id")));
        String actorRoles = trimToNull(firstNonBlank(actorRolesOverride, header(request, "X-User-Roles")));
        String actorType = StringUtils.hasText(actorSub) ? ACTOR_TYPE_USER : SYSTEM_SOURCE;
        String changeSource = resolveChangeSource(changeSourceOverride, request);
        String clientIp = normalizeIp(firstNonBlank(header(request, "X-Audit-Client-Ip"), header(request, "X-Forwarded-For"), request == null ? null : request.getRemoteAddr()));
        String userAgent = truncate(trimToNull(firstNonBlank(header(request, "X-Audit-User-Agent"), header(request, "User-Agent"))), 512);
        String requestId = truncate(trimToNull(header(request, "X-Request-Id")), 100);
        return new AccessAuditRequestContext(actorSub, actorTenantId, actorRoles, actorType, changeSource, clientIp, userAgent, requestId);
    }

    private String resolveChangeSource(String changeSourceOverride, HttpServletRequest request) {
        if (StringUtils.hasText(changeSourceOverride)) {
            return changeSourceOverride.trim();
        }
        return request != null ? ADMIN_API_SOURCE : SYSTEM_SOURCE;
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
