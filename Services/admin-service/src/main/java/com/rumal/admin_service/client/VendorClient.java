package com.rumal.admin_service.client;

import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VendorClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Qualifier("loadBalancedRestClientBuilder")
    private final RestClient.Builder lbRestClientBuilder;

    public VendorClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public List<Map<String, Object>> listAll(String internalAuth) {
        return getList("/admin/vendors", internalAuth);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return getList("/admin/vendors/deleted", internalAuth);
    }

    public Map<String, Object> getById(UUID id, String internalAuth) {
        return getMap("/admin/vendors/" + id, internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/vendors", request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/vendors/" + id, request, internalAuth);
    }

    public void delete(UUID id, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            rc.delete()
                    .uri(buildUri("/admin/vendors/" + id))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            Map<String, Object> body = rc.post()
                    .uri(buildUri("/admin/vendors/" + id + "/restore"))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(MAP_TYPE);
            if (body == null) {
                throw new ServiceUnavailableException("Vendor service returned an empty response", null);
            }
            return body;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth) {
        return getList("/admin/vendors/" + vendorId + "/users", internalAuth);
    }

    public List<Map<String, Object>> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getList("/internal/vendors/access/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public Map<String, Object> addVendorUser(UUID vendorId, Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/vendors/" + vendorId + "/users", request, internalAuth);
    }

    public Map<String, Object> updateVendorUser(UUID vendorId, UUID membershipId, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/vendors/" + vendorId + "/users/" + membershipId, request, internalAuth);
    }

    public void removeVendorUser(UUID vendorId, UUID membershipId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            rc.delete()
                    .uri(buildUri("/admin/vendors/" + vendorId + "/users/" + membershipId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    private List<Map<String, Object>> getList(String path, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<Map<String, Object>> response = rc.get()
                    .uri(buildUri(path))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(LIST_MAP_TYPE);
            if (response == null) {
                throw new ServiceUnavailableException("Vendor service returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    private Map<String, Object> getMap(String path, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            Map<String, Object> response = rc.get()
                    .uri(buildUri(path))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw new ServiceUnavailableException("Vendor service returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            RestClient.RequestBodySpec spec = switch (method) {
                case "POST" -> rc.post().uri(buildUri(path));
                case "PUT" -> rc.put().uri(buildUri(path));
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };
            Map<String, Object> response = spec
                    .header("X-Internal-Auth", internalAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                throw new ServiceUnavailableException("Vendor service returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
    }

    private DownstreamHttpException toDownstreamHttpException(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String message;
        if (StringUtils.hasText(body)) {
            String compactBody = body.replaceAll("\\s+", " ").trim();
            if (compactBody.length() > 300) {
                compactBody = compactBody.substring(0, 300) + "...";
            }
            message = "Vendor service responded with " + ex.getStatusCode().value() + ": " + compactBody;
        } else {
            message = "Vendor service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://vendor-service" + path);
    }
}
