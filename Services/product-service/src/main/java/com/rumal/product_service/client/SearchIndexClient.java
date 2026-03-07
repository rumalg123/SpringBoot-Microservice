package com.rumal.product_service.client;

import com.rumal.product_service.dto.SearchProductIndexRequest;
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
public class SearchIndexClient {

    private final RestClient restClient;
    private final String internalAuth;

    public SearchIndexClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalAuth = internalAuth;
    }

    @Retry(name = "searchService")
    @CircuitBreaker(name = "searchService", fallbackMethod = "fallbackUpsertProduct")
    public void upsertProduct(SearchProductIndexRequest request) {
        try {
            restClient.put()
                    .uri(buildUri("/internal/search/index/" + request.id()))
                    .header("X-Internal-Auth", internalAuth)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Search service upsert failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Search service unavailable for product upsert", ex);
        }
    }

    @Retry(name = "searchService")
    @CircuitBreaker(name = "searchService", fallbackMethod = "fallbackDeleteProduct")
    public void deleteProduct(UUID productId) {
        try {
            restClient.delete()
                    .uri(buildUri("/internal/search/index/" + productId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Search service delete failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Search service unavailable for product delete", ex);
        }
    }

    @SuppressWarnings("unused")
    public void fallbackUpsertProduct(SearchProductIndexRequest request, Throwable ex) {
        throw new ServiceUnavailableException("Search service unavailable for product upsert. Retry later.", ex);
    }

    @SuppressWarnings("unused")
    public void fallbackDeleteProduct(UUID productId, Throwable ex) {
        throw new ServiceUnavailableException("Search service unavailable for product delete. Retry later.", ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://search-service" + path);
    }
}
