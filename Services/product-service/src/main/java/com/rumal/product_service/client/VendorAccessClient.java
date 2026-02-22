package com.rumal.product_service.client;

import com.rumal.product_service.dto.VendorAccessMembershipResponse;
import com.rumal.product_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;

@Component
public class VendorAccessClient {

    private static final ParameterizedTypeReference<List<VendorAccessMembershipResponse>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;

    public VendorAccessClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public List<VendorAccessMembershipResponse> listAccessibleVendorsByKeycloakUser(String keycloakUserId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<VendorAccessMembershipResponse> response = rc.get()
                    .uri(buildUri("/internal/vendors/access/by-keycloak/" + keycloakUserId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(LIST_TYPE);
            return response == null ? List.of() : response;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Vendor service access lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable for access lookup", ex);
        }
    }

    private URI buildUri(String path) {
        return URI.create("http://vendor-service" + path);
    }
}
