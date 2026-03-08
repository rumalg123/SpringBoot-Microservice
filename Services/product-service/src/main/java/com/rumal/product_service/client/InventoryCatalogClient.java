package com.rumal.product_service.client;

import com.rumal.product_service.dto.InventoryCatalogSyncRequest;
import com.rumal.product_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.UUID;

@Component
public class InventoryCatalogClient {

    private final RestClient restClient;
    private final String internalAuth;

    public InventoryCatalogClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalAuth = internalAuth;
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackUpsertProduct")
    public void upsertProduct(InventoryCatalogSyncRequest request) {
        try {
            restClient.put()
                    .uri(buildUri("/internal/inventory/catalog/products/" + request.productId()))
                    .header("X-Internal-Auth", internalAuth)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Inventory service catalog sync failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Inventory service unavailable for catalog sync", ex);
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackDeleteProduct")
    public void deleteProduct(UUID productId) {
        try {
            restClient.delete()
                    .uri(buildUri("/internal/inventory/catalog/products/" + productId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Inventory service catalog delete failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Inventory service unavailable for catalog delete", ex);
        }
    }

    @SuppressWarnings("unused")
    public void fallbackUpsertProduct(InventoryCatalogSyncRequest request, Throwable ex) {
        throw new ServiceUnavailableException("Inventory service unavailable for product catalog sync. Retry later.", ex);
    }

    @SuppressWarnings("unused")
    public void fallbackDeleteProduct(UUID productId, Throwable ex) {
        throw new ServiceUnavailableException("Inventory service unavailable for product catalog delete. Retry later.", ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://inventory-service" + path);
    }
}
