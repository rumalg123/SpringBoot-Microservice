package com.rumal.admin_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
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
import java.util.function.Supplier;

@Component
public class VendorClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Qualifier("loadBalancedRestClientBuilder")
    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final ObjectMapper objectMapper;

    public VendorClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry,
            ObjectMapper objectMapper
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listAll(String internalAuth) {
        return listAll(internalAuth, null, null);
    }

    public List<Map<String, Object>> listAll(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList("/admin/vendors", internalAuth, userSub, userRoles);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return listDeleted(internalAuth, null, null);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList("/admin/vendors/deleted", internalAuth, userSub, userRoles);
    }

    public Map<String, Object> getById(UUID id, String internalAuth) {
        return getById(id, internalAuth, null, null);
    }

    public Map<String, Object> getById(UUID id, String internalAuth, String userSub, String userRoles) {
        return getMap("/admin/vendors/" + id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> getDeletionEligibility(UUID id, String internalAuth) {
        return getDeletionEligibility(id, internalAuth, null, null);
    }

    public Map<String, Object> getDeletionEligibility(UUID id, String internalAuth, String userSub, String userRoles) {
        return getMap("/admin/vendors/" + id + "/deletion-eligibility", internalAuth, userSub, userRoles);
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
        runVendorVoid(() -> {
            RestClient rc = restClient;
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
        });
    }

    public Map<String, Object> stopReceivingOrders(UUID id, String internalAuth) {
        return postNoBody("/admin/vendors/" + id + "/stop-orders", internalAuth);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return stopReceivingOrders(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/stop-orders", internalAuth, userSub, userRoles, idempotencyKey)
                : jsonPost("/admin/vendors/" + id + "/stop-orders", request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, String internalAuth) {
        return postNoBody("/admin/vendors/" + id + "/resume-orders", internalAuth);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return resumeReceivingOrders(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/resume-orders", internalAuth, userSub, userRoles, idempotencyKey)
                : jsonPost("/admin/vendors/" + id + "/resume-orders", request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> approveVerification(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/verify", internalAuth, userSub, userRoles, idempotencyKey)
                : jsonPost("/admin/vendors/" + id + "/verify", request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> rejectVerification(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return request == null
                ? postNoBody("/admin/vendors/" + id + "/reject-verification", internalAuth, userSub, userRoles, idempotencyKey)
                : jsonPost("/admin/vendors/" + id + "/reject-verification", request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return restore(id, null, internalAuth, null, null);
    }

    public Map<String, Object> restore(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return restore(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> restore(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return runVendorCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec spec = rc.post().uri(buildUri("/admin/vendors/" + id + "/restore"));
                RestClient.RequestHeadersSpec<?> headersSpec = applyIdempotencyHeader(
                        applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles),
                        idempotencyKey
                );
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
        });
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth) {
        return listVendorUsers(vendorId, internalAuth, null, null);
    }

    public List<Map<String, Object>> listVendorUsersInternal(UUID vendorId, String internalAuth) {
        return getList("/internal/vendors/access/" + vendorId + "/users", internalAuth);
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth, String userSub, String userRoles) {
        return getList("/admin/vendors/" + vendorId + "/users", internalAuth, userSub, userRoles);
    }

    public Map<String, Object> listLifecycleAudit(UUID vendorId, String internalAuth) {
        return listLifecycleAudit(vendorId, internalAuth, null, null);
    }

    public Map<String, Object> listLifecycleAudit(UUID vendorId, String internalAuth, String userSub, String userRoles) {
        return getMap("/admin/vendors/" + vendorId + "/lifecycle-audit", internalAuth, userSub, userRoles);
    }

    public Map<String, Object> requestDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return requestDelete(vendorId, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> requestDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return (request == null || request.isEmpty())
                ? postNoBody("/admin/vendors/" + vendorId + "/delete-request", internalAuth, userSub, userRoles, idempotencyKey)
                : jsonPost("/admin/vendors/" + vendorId + "/delete-request", request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public void confirmDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        confirmDelete(vendorId, request, internalAuth, userSub, userRoles, null);
    }

    public void confirmDelete(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        runVendorVoid(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec spec = rc.post().uri(buildUri("/admin/vendors/" + vendorId + "/confirm-delete"));
                RestClient.RequestHeadersSpec<?> headersSpec = applyIdempotencyHeader(
                        applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles),
                        idempotencyKey
                );
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
        });
    }

    public List<Map<String, Object>> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getList("/internal/vendors/access/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public Map<String, Object> addVendorUser(UUID vendorId, Map<String, Object> request, String internalAuth) {
        return addVendorUser(vendorId, request, internalAuth, null, null, null);
    }

    public Map<String, Object> addVendorUser(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return addVendorUser(vendorId, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> addVendorUser(
            UUID vendorId,
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        return jsonRequest(
                "POST",
                "/admin/vendors/" + vendorId + "/users",
                request,
                internalAuth,
                userSub,
                userRoles,
                ensureIdempotencyKey(idempotencyKey)
        );
    }

    public Map<String, Object> updateVendorUser(UUID vendorId, UUID membershipId, Map<String, Object> request, String internalAuth) {
        return updateVendorUser(vendorId, membershipId, request, internalAuth, null, null, null);
    }

    public Map<String, Object> updateVendorUser(UUID vendorId, UUID membershipId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return updateVendorUser(vendorId, membershipId, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> updateVendorUser(
            UUID vendorId,
            UUID membershipId,
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        return jsonRequest(
                "PUT",
                "/admin/vendors/" + vendorId + "/users/" + membershipId,
                request,
                internalAuth,
                userSub,
                userRoles,
                ensureIdempotencyKey(idempotencyKey)
        );
    }

    public void removeVendorUser(UUID vendorId, UUID membershipId, String internalAuth) {
        removeVendorUser(vendorId, membershipId, internalAuth, null, null, null);
    }

    public void removeVendorUser(UUID vendorId, UUID membershipId, String internalAuth, String userSub, String userRoles) {
        removeVendorUser(vendorId, membershipId, internalAuth, userSub, userRoles, null);
    }

    public void removeVendorUser(
            UUID vendorId,
            UUID membershipId,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        String resolvedIdempotencyKey = ensureIdempotencyKey(idempotencyKey);
        runVendorVoid(() -> {
            RestClient rc = restClient;
            try {
                applyIdempotencyHeader(
                        applyActorHeaders(rc.delete()
                                .uri(buildUri("/admin/vendors/" + vendorId + "/users/" + membershipId))
                                .header("X-Internal-Auth", internalAuth), userSub, userRoles),
                        resolvedIdempotencyKey
                )
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
            } catch (IllegalStateException ex) {
                throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(String path, String internalAuth) {
        return getPagedContentList(path, internalAuth, null, null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(String path, String internalAuth, String userSub, String userRoles) {
        Map<String, Object> page = getMap(path, internalAuth, userSub, userRoles);
        Object content = page.get("content");
        if (content instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> getList(String path, String internalAuth) {
        return getList(path, internalAuth, null, null);
    }

    private List<Map<String, Object>> getList(String path, String internalAuth, String userSub, String userRoles) {
        return runVendorCall(() -> {
            RestClient rc = restClient;
            try {
                List<Map<String, Object>> response = applyActorHeaders(rc.get()
                        .uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles)
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
        });
    }

    private Map<String, Object> getMap(String path, String internalAuth) {
        return getMap(path, internalAuth, null, null);
    }

    private Map<String, Object> getMap(String path, String internalAuth, String userSub, String userRoles) {
        return runVendorCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = applyActorHeaders(rc.get()
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
        });
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth) {
        return jsonRequest(method, path, request, internalAuth, null, null);
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest(method, path, request, internalAuth, userSub, userRoles, null);
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return runVendorCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec spec = switch (method) {
                    case "POST" -> rc.post().uri(buildUri(path));
                    case "PUT" -> rc.put().uri(buildUri(path));
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };
                Map<String, Object> response = ((RestClient.RequestBodySpec) applyIdempotencyHeader(
                                applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles),
                                idempotencyKey
                        ))
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
        });
    }

    private Map<String, Object> postNoBody(String path, String internalAuth) {
        return postNoBody(path, internalAuth, null, null);
    }

    private Map<String, Object> postNoBody(String path, String internalAuth, String userSub, String userRoles) {
        return postNoBody(path, internalAuth, userSub, userRoles, null);
    }

    private Map<String, Object> postNoBody(String path, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return runVendorCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = applyIdempotencyHeader(
                                applyActorHeaders(rc.post()
                                        .uri(buildUri(path))
                                        .header("X-Internal-Auth", internalAuth), userSub, userRoles),
                                idempotencyKey
                        )
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
        });
    }

    private Map<String, Object> jsonPost(String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonPost(path, request, internalAuth, userSub, userRoles, null);
    }

    private Map<String, Object> jsonPost(String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return jsonRequest("POST", path, request, internalAuth, userSub, userRoles, idempotencyKey);
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
            String compactBody = extractDownstreamMessage(body);
            if (compactBody.length() > 300) {
                compactBody = compactBody.substring(0, 300) + "...";
            }
            message = "Vendor service responded with " + ex.getStatusCode().value() + ": " + compactBody;
        } else {
            message = "Vendor service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private RestClient.RequestHeadersSpec<?> applyIdempotencyHeader(RestClient.RequestHeadersSpec<?> spec, String idempotencyKey) {
        return ClientRequestUtils.applyIdempotencyHeader(spec, idempotencyKey);
    }

    private String ensureIdempotencyKey(String idempotencyKey) {
        String resolved = ClientRequestUtils.resolveIdempotencyKey(idempotencyKey);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        return UUID.randomUUID().toString();
    }

    private String extractDownstreamMessage(String body) {
        String compactBody = body.replaceAll("\\s+", " ").trim();
        if (!(compactBody.startsWith("{") && compactBody.endsWith("}"))) {
            return compactBody;
        }
        try {
            JsonNode root = objectMapper.readTree(compactBody);
            if (root.hasNonNull("message") && root.get("message").isTextual()) {
                String message = root.get("message").asText().trim();
                if (StringUtils.hasText(message)) {
                    return message;
                }
            }
            if (root.hasNonNull("error") && root.get("error").isTextual()) {
                String error = root.get("error").asText().trim();
                if (StringUtils.hasText(error)) {
                    return error;
                }
            }
        } catch (Exception ignored) {
            return compactBody;
        }
        return compactBody;
    }

    private URI buildUri(String path) {
        return URI.create("http://vendor-service" + path);
    }

    private <T> T runVendorCall(Supplier<T> action) {
        var retry = retryRegistry.retry("admin-vendor-client");
        Supplier<T> retryableAction = io.github.resilience4j.retry.Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("admin-vendor-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", throwable);
                        })
        );
        return retryableAction.get();
    }

    private void runVendorVoid(Runnable action) {
        runVendorCall(() -> {
            action.run();
            return Boolean.TRUE;
        });
    }
}
