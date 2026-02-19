package com.rumal.product_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "object-storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket
) {
}
