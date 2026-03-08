package com.rumal.inventory_service.client;

import com.rumal.inventory_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.UUID;

@Component
public class ProductSearchSyncClient {

    private final RestClient restClient;
    private final String internalAuth;

    public ProductSearchSyncClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalAuth = internalAuth == null ? "" : internalAuth.trim();
    }

    public void requestSearchSync(UUID productId) {
        try {
            restClient.post()
                    .uri(buildUri("/internal/products/search-sync/" + productId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Product service search sync failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Product service unavailable for search sync", ex);
        }
    }

    private URI buildUri(String path) {
        return URI.create("http://product-service" + path);
    }
}
