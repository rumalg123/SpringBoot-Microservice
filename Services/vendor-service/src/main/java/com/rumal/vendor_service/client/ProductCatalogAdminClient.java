package com.rumal.vendor_service.client;

import com.rumal.vendor_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class ProductCatalogAdminClient {

    private final RestClient.Builder lbRestClientBuilder;

    public ProductCatalogAdminClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public void evictPublicCachesForVendor(UUID vendorId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            rc.post()
                    .uri("http://product-service/internal/products/cache/vendors/{vendorId}/evict", vendorId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Product service cache eviction failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Product service unavailable for cache eviction", ex);
        }
    }
}
