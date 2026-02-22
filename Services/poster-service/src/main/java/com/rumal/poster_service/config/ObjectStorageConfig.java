package com.rumal.poster_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "object-storage", name = "enabled", havingValue = "true")
    public S3Client objectStorageS3Client(ObjectStorageProperties properties) {
        String endpoint = normalizeEndpoint(properties.endpoint());
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private String normalizeEndpoint(String rawEndpoint) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            throw new IllegalStateException("object-storage.endpoint is required when object storage is enabled");
        }
        String value = rawEndpoint.trim();
        String keyPrefix = "object_storage_endpoint=";
        if (value.toLowerCase(Locale.ROOT).startsWith(keyPrefix)) {
            value = value.substring(keyPrefix.length()).trim();
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalStateException("object-storage.endpoint must start with http:// or https://");
        }
        return value;
    }
}
