package com.rumal.vendor_service.client;

import com.rumal.vendor_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class ProductCatalogAdminClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogAdminClient.class);

    private final RestClient.Builder lbRestClientBuilder;

    public ProductCatalogAdminClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackEvictPublicCachesForVendor")
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

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackDeactivateAllByVendor")
    public void deactivateAllByVendor(UUID vendorId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            rc.post()
                    .uri("http://product-service/internal/products/vendors/{vendorId}/deactivate-all", vendorId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Product deactivation failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Product service unavailable for product deactivation", ex);
        }
    }

    @SuppressWarnings("unused")
    public void fallbackEvictPublicCachesForVendor(UUID vendorId, String internalAuth, Throwable ex) {
        log.warn("Product service unavailable for cache eviction (vendor={}). Stale cache entries may persist until TTL expiry.", vendorId, ex);
    }

    @SuppressWarnings("unused")
    public void fallbackDeactivateAllByVendor(UUID vendorId, String internalAuth, Throwable ex) {
        log.warn("Product service unavailable for product deactivation (vendor={}). Products may remain active until manual cleanup.", vendorId, ex);
    }
}
