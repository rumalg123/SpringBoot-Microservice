package com.rumal.admin_service.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class ClientRequestUtils {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private ClientRequestUtils() {
    }

    static String resolveIdempotencyKey(String explicitKey) {
        if (StringUtils.hasText(explicitKey)) {
            return explicitKey.trim();
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String currentRequestKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (!StringUtils.hasText(currentRequestKey)) {
            return null;
        }
        return currentRequestKey.trim();
    }
}
