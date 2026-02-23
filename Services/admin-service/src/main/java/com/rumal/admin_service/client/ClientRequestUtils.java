package com.rumal.admin_service.client;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

final class ClientRequestUtils {

    private ClientRequestUtils() {
    }

    static RestClient.RequestHeadersSpec<?> applyIdempotencyHeader(
            RestClient.RequestHeadersSpec<?> spec,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return spec;
        }
        return spec.header("Idempotency-Key", idempotencyKey.trim());
    }
}
