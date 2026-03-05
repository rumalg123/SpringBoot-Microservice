package com.rumal.cart_service.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Configuration
public class HttpClientConfig {

    private static final String HMAC_ALGO = "HmacSHA256";

    @Value("${http.client.connect-timeout-seconds:2}")
    private int connectTimeoutSeconds;

    @Value("${http.client.response-timeout-seconds:5}")
    private int responseTimeoutSeconds;

    @Value("${http.client.idle-evict-seconds:30}")
    private int idleEvictSeconds;

    @Value("${http.client.max-connections:100}")
    private int maxConnections;

    @Value("${http.client.max-connections-per-route:20}")
    private int maxConnectionsPerRoute;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        var connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                                .build())
                        .setMaxConnTotal(Math.max(10, maxConnections))
                        .setMaxConnPerRoute(Math.max(5, maxConnectionsPerRoute))
                        .build();

        var httpClient =
                HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setConnectionRequestTimeout(Timeout.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                                .setResponseTimeout(Timeout.ofSeconds(Math.max(1, responseTimeoutSeconds)))
                                .build())
                        .evictExpiredConnections()
                        .evictIdleConnections(TimeValue.ofSeconds(Math.max(1, idleEvictSeconds)))
                        .build();

        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    applyInternalHmacHeaders(request, body);
                    return execution.execute(request, body);
                });
    }

    private void applyInternalHmacHeaders(org.springframework.http.HttpRequest request, byte[] body) {
        String secret = internalAuthSharedSecret == null ? "" : internalAuthSharedSecret.trim();
        if (secret.isEmpty()) {
            return;
        }

        String internalHeader = request.getHeaders().getFirst("X-Internal-Auth");
        if (internalHeader == null || internalHeader.isBlank()) {
            return;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();
        String path = request.getURI().getRawPath();
        String bodyHash = computeBodyHash(method, body);

        String payload = timestamp + ":" + method + ":" + path + ":" + bodyHash;
        String signature = computeHmac(secret, payload);

        request.getHeaders().set("X-Internal-Timestamp", timestamp);
        request.getHeaders().set("X-Internal-Signature", signature);
        request.getHeaders().set("X-Internal-Path", path);
        request.getHeaders().set("X-Internal-Body-Hash", bodyHash);
    }

    private String computeBodyHash(String method, byte[] body) {
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return "";
        }
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception ex) {
            return "";
        }
    }

    private String computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return "";
        }
    }
}
