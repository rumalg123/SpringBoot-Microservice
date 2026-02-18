package com.rumal.order_service.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

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
                                .setConnectTimeout(Timeout.ofSeconds(2))   // CONNECT timeout
                                .build())
                        .setDefaultSocketConfig(SocketConfig.custom()
                                .setSoTimeout(Timeout.ofSeconds(3))        // READ timeout
                                .build())
                        .build();

        var httpClient =
                HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .evictExpiredConnections()
                        .evictIdleConnections(TimeValue.ofSeconds(30))
                        .build();

        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder().requestFactory(requestFactory);
    }


}

