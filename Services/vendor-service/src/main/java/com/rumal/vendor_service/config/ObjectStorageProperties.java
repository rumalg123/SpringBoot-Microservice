package com.rumal.vendor_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "object-storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration presignExpiry
) {
}
