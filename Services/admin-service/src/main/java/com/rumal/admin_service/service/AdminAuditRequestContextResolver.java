package com.rumal.admin_service.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AdminAuditRequestContextResolver {

    public AdminAuditRequestContext resolve(String actorKeycloakId, String actorRoles, String ipAddressOverride) {
        HttpServletRequest request = currentRequest();
        String resolvedActorKeycloakId = firstNonBlank(actorKeycloakId, header(request, "X-User-Sub"), "system");
        String resolvedActorRoles = trimToNull(firstNonBlank(actorRoles, header(request, "X-User-Roles")));
        String resolvedActorTenantId = trimToNull(header(request, "X-Actor-Tenant-Id"));
        String resolvedIpAddress = normalizeIp(firstNonBlank(ipAddressOverride, header(request, "X-Audit-Client-Ip"), header(request, "X-Forwarded-For"), request == null ? null : request.getRemoteAddr()));
        String resolvedUserAgent = truncate(trimToNull(firstNonBlank(header(request, "X-Audit-User-Agent"), header(request, "User-Agent"))), 512);
        String resolvedRequestId = truncate(trimToNull(header(request, "X-Request-Id")), 100);
        String actorType = StringUtils.hasText(resolvedActorKeycloakId) && !"system".equalsIgnoreCase(resolvedActorKeycloakId)
                ? "USER"
                : "SYSTEM";
        String changeSource = request != null ? "ADMIN_API" : "SYSTEM";
        return new AdminAuditRequestContext(
                resolvedActorKeycloakId,
                resolvedActorTenantId,
                resolvedActorRoles,
                actorType,
                changeSource,
                resolvedIpAddress,
                resolvedUserAgent,
                resolvedRequestId
        );
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
