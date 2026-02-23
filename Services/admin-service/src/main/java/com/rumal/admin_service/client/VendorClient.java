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

    public Map<String, Object> getDeletionEligibility(UUID id, String internalAuth) {
        return getMap("/admin/vendors/" + id + "/deletion-eligibility", internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/vendors", request, internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest("POST", "/admin/vendors", request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/vendors/" + id, request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest("PUT", "/admin/vendors/" + id, request, internalAuth, userSub, userRoles);
    }

    public void delete(UUID id, String internalAuth) {
        delete(id, internalAuth, null, null);
    }

    public void delete(UUID id, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            applyActorHeaders(rc.delete()
                    .uri(buildUri("/admin/vendors/" + id))
                    .header("X-Internal-Auth", internalAuth), userSub, userRoles)
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

    public Map<String, Object> stopReceivingOrders(UUID id, String internalAuth) {
        return postNoBody("/admin/vendors/" + id + "/stop-orders", internalAuth);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/stop-orders", internalAuth, userSub, userRoles)
                : jsonPost("/admin/vendors/" + id + "/stop-orders", request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, String internalAuth) {
        return postNoBody("/admin/vendors/" + id + "/resume-orders", internalAuth);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/resume-orders", internalAuth, userSub, userRoles)
                : jsonPost("/admin/vendors/" + id + "/resume-orders", request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return restore(id, null, internalAuth, null, null);
    }

    public Map<String, Object> restore(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            RestClient.RequestBodySpec spec = rc.post().uri(buildUri("/admin/vendors/" + id + "/restore"));
            RestClient.RequestHeadersSpec<?> headersSpec = applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles);
            Map<String, Object> body;
            if (request == null || request.isEmpty()) {
                body = headersSpec.retrieve().body(MAP_TYPE);
            } else {
                body = ((RestClient.RequestBodySpec) headersSpec)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(MAP_TYPE);
            }
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

    public List<Map<String, Object>> listLifecycleAudit(UUID vendorId, String internalAuth) {
        return getList("/admin/vendors/" + vendorId + "/lifecycle-audit", internalAuth);
    }

    public Map<String, Object> requestDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return (request == null || request.isEmpty())
                ? postNoBody("/admin/vendors/" + vendorId + "/delete-request", internalAuth, userSub, userRoles)
                : jsonPost("/admin/vendors/" + vendorId + "/delete-request", request, internalAuth, userSub, userRoles);
    }

    public void confirmDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            RestClient.RequestBodySpec spec = rc.post().uri(buildUri("/admin/vendors/" + vendorId + "/confirm-delete"));
            RestClient.RequestHeadersSpec<?> headersSpec = applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles);
            if (request == null || request.isEmpty()) {
                headersSpec.retrieve().toBodilessEntity();
            } else {
                ((RestClient.RequestBodySpec) headersSpec)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
            }
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        } catch (IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
        }
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
        return jsonRequest(method, path, request, internalAuth, null, null);
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            RestClient.RequestBodySpec spec = switch (method) {
                case "POST" -> rc.post().uri(buildUri(path));
                case "PUT" -> rc.put().uri(buildUri(path));
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };
            Map<String, Object> response = ((RestClient.RequestBodySpec) applyActorHeaders(spec
                    .header("X-Internal-Auth", internalAuth), userSub, userRoles))
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

    private Map<String, Object> postNoBody(String path, String internalAuth) {
        return postNoBody(path, internalAuth, null, null);
    }

    private Map<String, Object> postNoBody(String path, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            Map<String, Object> response = applyActorHeaders(rc.post()
                    .uri(buildUri(path))
                    .header("X-Internal-Auth", internalAuth), userSub, userRoles)
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

    private Map<String, Object> jsonPost(String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest("POST", path, request, internalAuth, userSub, userRoles);
    }

    private RestClient.RequestHeadersSpec<?> applyActorHeaders(RestClient.RequestHeadersSpec<?> spec, String userSub, String userRoles) {
        RestClient.RequestHeadersSpec<?> next = spec;
        if (StringUtils.hasText(userSub)) {
            next = next.header("X-User-Sub", userSub);
        }
        if (StringUtils.hasText(userRoles)) {
            next = next.header("X-User-Roles", userRoles);
        }
        return next;
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
