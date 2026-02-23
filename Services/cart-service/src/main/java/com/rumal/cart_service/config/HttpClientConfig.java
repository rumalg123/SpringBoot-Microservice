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

@Configuration
public class HttpClientConfig {

    @Value("${http.client.connect-timeout-seconds:2}")
    private int connectTimeoutSeconds;

    @Value("${http.client.response-timeout-seconds:5}")
    private int responseTimeoutSeconds;

    @Value("${http.client.idle-evict-seconds:30}")
    private int idleEvictSeconds;

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
        return RestClient.builder().requestFactory(requestFactory);
    }
}
