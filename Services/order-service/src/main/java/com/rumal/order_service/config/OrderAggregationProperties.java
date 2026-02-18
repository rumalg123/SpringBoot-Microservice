package com.rumal.order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.aggregation")
public record OrderAggregationProperties(
        CustomerDetailsMode customerDetailsMode
) {}
