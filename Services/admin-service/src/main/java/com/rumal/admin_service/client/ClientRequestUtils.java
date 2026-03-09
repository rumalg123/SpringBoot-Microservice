package com.rumal.admin_service.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class ClientRequestUtils {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private ClientRequestUtils() {
    }

    static RestClient.RequestHeadersSpec<?> applyIdempotencyHeader(
            RestClient.RequestHeadersSpec<?> spec,
            String idempotencyKey
    ) {
        String resolvedKey = resolveIdempotencyKey(idempotencyKey);
        if (!StringUtils.hasText(resolvedKey)) {
            return spec;
        }
        return spec.header(IDEMPOTENCY_HEADER, resolvedKey);
    }

    static String resolveIdempotencyKey(String explicitKey) {
        if (StringUtils.hasText(explicitKey)) {
            return explicitKey.trim();
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        if (request == null) {
            return null;
        }
        String currentRequestKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (!StringUtils.hasText(currentRequestKey)) {
            return null;
        }
        return currentRequestKey.trim();
    }
}
