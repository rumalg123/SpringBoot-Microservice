package com.rumal.product_service.client;

import com.rumal.product_service.dto.StockAvailabilitySummary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private static final ParameterizedTypeReference<List<StockAvailabilitySummary>> SUMMARY_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String internalSharedSecret;

    public InventoryClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackGetStockSummary")
    public StockAvailabilitySummary getStockSummary(UUID productId) {
        try {
            return restClient
                    .get()
                    .uri("http://inventory-service/internal/inventory/products/{productId}/stock-summary", productId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(StockAvailabilitySummary.class);
        } catch (RestClientException ex) {
            log.warn("Failed to get stock summary for product {}: {}", productId, ex.getMessage());
            throw ex;
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackGetBatchStockSummary")
    public List<StockAvailabilitySummary> getBatchStockSummary(List<UUID> productIds) {
        try {
            List<StockAvailabilitySummary> result = restClient
                    .post()
                    .uri("http://inventory-service/internal/inventory/products/stock-summary/batch")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(Map.of("productIds", productIds))
                    .retrieve()
                    .body(SUMMARY_LIST_TYPE);
            return result == null ? List.of() : result;
        } catch (RestClientException ex) {
            log.warn("Failed to get batch stock summary: {}", ex.getMessage());
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public StockAvailabilitySummary fallbackGetStockSummary(UUID productId, Throwable ex) {
        log.warn("Inventory service unavailable for product {}. Returning null stock.", productId);
        return null;
    }

    @SuppressWarnings("unused")
    public List<StockAvailabilitySummary> fallbackGetBatchStockSummary(List<UUID> productIds, Throwable ex) {
        log.warn("Inventory service unavailable for batch stock summary. Returning empty list.");
        return List.of();
    }
}
