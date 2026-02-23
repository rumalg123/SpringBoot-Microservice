package com.rumal.product_service.client;

import com.rumal.product_service.dto.PlatformAccessLookupResponse;
import com.rumal.product_service.dto.VendorStaffAccessLookupResponse;
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
public class AccessClient {

    private static final ParameterizedTypeReference<List<VendorStaffAccessLookupResponse>> VENDOR_ACCESS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;

    public AccessClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public PlatformAccessLookupResponse getPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            PlatformAccessLookupResponse response = rc.get()
                    .uri(buildUri("/internal/access/platform/by-keycloak/" + keycloakUserId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(PlatformAccessLookupResponse.class);
            return response == null ? new PlatformAccessLookupResponse(keycloakUserId, false, java.util.Set.of()) : response;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Access service platform lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Access service unavailable for platform lookup", ex);
        }
    }

    public List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<VendorStaffAccessLookupResponse> response = rc.get()
                    .uri(buildUri("/internal/access/vendors/by-keycloak/" + keycloakUserId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VENDOR_ACCESS_LIST_TYPE);
            return response == null ? List.of() : response;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Access service vendor lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Access service unavailable for vendor lookup", ex);
        }
    }

    private URI buildUri(String path) {
        return URI.create("http://access-service" + path);
    }
}
