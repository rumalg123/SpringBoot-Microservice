package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.AccessAuditQuery;
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
public class AccessClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String PLATFORM_STAFF_PATH = "/admin/platform-staff";
    private static final String PLATFORM_STAFF_PATH_PREFIX = PLATFORM_STAFF_PATH + "/";
    private static final String VENDOR_STAFF_PATH = "/admin/vendor-staff";
    private static final String VENDOR_STAFF_PATH_PREFIX = VENDOR_STAFF_PATH + "/";
    private static final String RESTORE_SUFFIX = "/restore";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    public AccessClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
    }

    public Map<String, Object> getPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getMap("/internal/access/platform/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listVendorStaffAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getList("/internal/access/vendors/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listPlatformStaff(String internalAuth) {
        return listPlatformStaff(internalAuth, null, null);
    }

    public List<Map<String, Object>> listPlatformStaff(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList(PLATFORM_STAFF_PATH, internalAuth, actorHeaders(userSub, userRoles, null, null));
    }

    public List<Map<String, Object>> listDeletedPlatformStaff(String internalAuth) {
        return listDeletedPlatformStaff(internalAuth, null, null);
    }

    public List<Map<String, Object>> listDeletedPlatformStaff(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList(PLATFORM_STAFF_PATH + "/deleted", internalAuth, actorHeaders(userSub, userRoles, null, null));
    }

    public Map<String, Object> getPlatformStaffById(UUID id, String internalAuth) {
        return getPlatformStaffById(id, internalAuth, null, null);
    }

    public Map<String, Object> getPlatformStaffById(UUID id, String internalAuth, String userSub, String userRoles) {
        return getMap(platformStaffPath(id), internalAuth, actorHeaders(userSub, userRoles, null, null));
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", PLATFORM_STAFF_PATH, request, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("POST", PLATFORM_STAFF_PATH, request, internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", platformStaffPath(id), request, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("PUT", platformStaffPath(id), request, internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public void deletePlatformStaff(UUID id, String internalAuth) {
        deleteNoContent(platformStaffPath(id), internalAuth, actorHeaders(null, null, null, null));
    }

    public void deletePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        deleteNoContent(platformStaffPath(id), internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth) {
        return postNoBody(platformStaffPath(id) + RESTORE_SUFFIX, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return postNoBody(platformStaffPath(id) + RESTORE_SUFFIX, internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public List<Map<String, Object>> listVendorStaff(UUID vendorId, String internalAuth) {
        return listVendorStaff(vendorId, internalAuth, null, null);
    }

    public List<Map<String, Object>> listVendorStaff(UUID vendorId, String internalAuth, String userRoles, UUID callerVendorId) {
        String path = vendorId == null ? VENDOR_STAFF_PATH : VENDOR_STAFF_PATH + "?vendorId=" + vendorId;
        return getPagedContentList(path, internalAuth, actorHeaders(null, userRoles, null, callerVendorId));
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth) {
        return listDeletedVendorStaff(internalAuth, null, null);
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth, String userRoles, UUID callerVendorId) {
        return getPagedContentList(VENDOR_STAFF_PATH + "/deleted", internalAuth, actorHeaders(null, userRoles, null, callerVendorId));
    }

    public Map<String, Object> listAccessAudit(AccessAuditQuery query, String internalAuth, String userRoles, UUID callerVendorId) {
        StringBuilder path = new StringBuilder("/admin/access-audit");
        String sep = "?";
        if (StringUtils.hasText(query.targetType())) {
            path.append(sep).append("targetType=").append(query.targetType().trim());
            sep = "&";
        }
        if (query.targetId() != null) {
            path.append(sep).append("targetId=").append(query.targetId());
            sep = "&";
        }
        if (query.vendorId() != null) {
            path.append(sep).append("vendorId=").append(query.vendorId());
            sep = "&";
        }
        if (StringUtils.hasText(query.action())) {
            path.append(sep).append("action=").append(query.action().trim());
            sep = "&";
        }
        if (StringUtils.hasText(query.actorQuery())) {
            path.append(sep).append("actorQuery=").append(java.net.URLEncoder.encode(query.actorQuery().trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (StringUtils.hasText(query.from())) {
            path.append(sep).append("from=").append(java.net.URLEncoder.encode(query.from().trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (StringUtils.hasText(query.to())) {
            path.append(sep).append("to=").append(java.net.URLEncoder.encode(query.to().trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (query.page() != null) {
            path.append(sep).append("page=").append(query.page());
            sep = "&";
        }
        if (query.size() != null) {
            path.append(sep).append("size=").append(query.size());
            sep = "&";
        }
        if (query.limit() != null) {
            path.append(sep).append("limit=").append(query.limit());
        }
        return getMap(path.toString(), internalAuth, actorHeaders(null, userRoles, null, callerVendorId));
    }

    public Map<String, Object> getVendorStaffById(UUID id, String internalAuth) {
        return getVendorStaffById(id, internalAuth, null, null);
    }

    public Map<String, Object> getVendorStaffById(UUID id, String internalAuth, String userRoles, UUID callerVendorId) {
        return getMap(vendorStaffPath(id), internalAuth, actorHeaders(null, userRoles, null, callerVendorId));
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", VENDOR_STAFF_PATH, request, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("POST", VENDOR_STAFF_PATH, request, internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", vendorStaffPath(id), request, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("PUT", vendorStaffPath(id), request, internalAuth, actorHeaders(userSub, userRoles, actionReason, null));
    }

    public void deleteVendorStaff(UUID id, String internalAuth) {
        deleteNoContent(vendorStaffPath(id), internalAuth, actorHeaders(null, null, null, null));
    }

    public void deleteVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason, null);
    }

    public void deleteVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason, UUID callerVendorId) {
        deleteNoContent(vendorStaffPath(id), internalAuth, actorHeaders(userSub, userRoles, actionReason, callerVendorId));
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth) {
        return postNoBody(vendorStaffPath(id) + RESTORE_SUFFIX, internalAuth, actorHeaders(null, null, null, null));
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason, null);
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason, UUID callerVendorId) {
        return postNoBody(vendorStaffPath(id) + RESTORE_SUFFIX, internalAuth, actorHeaders(userSub, userRoles, actionReason, callerVendorId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(String path, String internalAuth) {
        return getPagedContentList(path, internalAuth, actorHeaders(null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(
            String path,
            String internalAuth,
            ActorHeaders actorHeaders
    ) {
        Map<String, Object> page = getMap(path, internalAuth, actorHeaders);
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
        return runAccessCall(() -> {
            RestClient rc = restClient;
            try {
                List<Map<String, Object>> response = rc.get().uri(buildUri(path))
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve().body(LIST_MAP_TYPE);
                return response == null ? List.of() : response;
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private Map<String, Object> getMap(String path, String internalAuth) {
        return getMap(path, internalAuth, actorHeaders(null, null, null, null));
    }

    private Map<String, Object> getMap(String path, String internalAuth, ActorHeaders actorHeaders) {
        return runAccessCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = applyActorHeaders(
                        rc.get().uri(buildUri(path)).header(INTERNAL_AUTH_HEADER, internalAuth),
                        actorHeaders
                )
                        .retrieve().body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Access service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth) {
        return jsonRequest(method, path, request, internalAuth, actorHeaders(null, null, null, null));
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth, ActorHeaders actorHeaders) {
        return runAccessCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec spec = switch (method) {
                    case "POST" -> rc.post().uri(buildUri(path));
                    case "PUT" -> rc.put().uri(buildUri(path));
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };
                Map<String, Object> response = applyIdempotencyHeader(
                                applyActorHeaders(spec.header(INTERNAL_AUTH_HEADER, internalAuth), actorHeaders)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Access service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private void deleteNoContent(String path, String internalAuth) {
        deleteNoContent(path, internalAuth, actorHeaders(null, null, null, null));
    }

    private void deleteNoContent(String path, String internalAuth, ActorHeaders actorHeaders) {
        runAccessVoid(() -> {
            RestClient rc = restClient;
            try {
                applyIdempotencyHeader(
                        applyActorHeaders(rc.delete().uri(buildUri(path)).header(INTERNAL_AUTH_HEADER, internalAuth), actorHeaders)
                )
                        .retrieve().toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private Map<String, Object> postNoBody(String path, String internalAuth) {
        return postNoBody(path, internalAuth, actorHeaders(null, null, null, null));
    }

    private Map<String, Object> postNoBody(String path, String internalAuth, ActorHeaders actorHeaders) {
        return runAccessCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = applyIdempotencyHeader(
                        applyActorHeaders(rc.post().uri(buildUri(path))
                                .header(INTERNAL_AUTH_HEADER, internalAuth), actorHeaders)
                )
                        .retrieve().body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Access service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private DownstreamHttpException toDownstream(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String message;
        if (StringUtils.hasText(body)) {
            String compact = body.replaceAll("\\s+", " ").trim();
            if (compact.length() > 300) {
                compact = compact.substring(0, 300) + "...";
            }
            message = "Access service responded with " + ex.getStatusCode().value() + ": " + compact;
        } else {
            message = "Access service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://access-service" + path);
    }

    private RestClient.RequestBodySpec applyIdempotencyHeader(RestClient.RequestBodySpec spec) {
        String resolvedKey = ClientRequestUtils.resolveIdempotencyKey(null);
        if (!StringUtils.hasText(resolvedKey)) {
            return spec;
        }
        return spec.header(ClientRequestUtils.IDEMPOTENCY_HEADER, resolvedKey);
    }

    private RestClient.RequestHeadersSpec<?> applyIdempotencyHeader(RestClient.RequestHeadersSpec<?> spec) {
        String resolvedKey = ClientRequestUtils.resolveIdempotencyKey(null);
        if (!StringUtils.hasText(resolvedKey)) {
            return spec;
        }
        return spec.header(ClientRequestUtils.IDEMPOTENCY_HEADER, resolvedKey);
    }

    private RestClient.RequestHeadersSpec<?> applyActorHeaders(RestClient.RequestHeadersSpec<?> spec, ActorHeaders actorHeaders) {
        RestClient.RequestHeadersSpec<?> next = spec;
        if (StringUtils.hasText(actorHeaders.userSub())) {
            next = next.header("X-User-Sub", actorHeaders.userSub());
        }
        if (StringUtils.hasText(actorHeaders.userRoles())) {
            next = next.header("X-User-Roles", actorHeaders.userRoles());
        }
        if (StringUtils.hasText(actorHeaders.actionReason())) {
            next = next.header("X-Action-Reason", actorHeaders.actionReason().trim());
        }
        if (actorHeaders.callerVendorId() != null) {
            next = next.header("X-Caller-Vendor-Id", String.valueOf(actorHeaders.callerVendorId()));
        }
        return next;
    }

    private RestClient.RequestBodySpec applyActorHeaders(RestClient.RequestBodySpec spec, ActorHeaders actorHeaders) {
        RestClient.RequestBodySpec next = spec;
        if (StringUtils.hasText(actorHeaders.userSub())) {
            next = next.header("X-User-Sub", actorHeaders.userSub());
        }
        if (StringUtils.hasText(actorHeaders.userRoles())) {
            next = next.header("X-User-Roles", actorHeaders.userRoles());
        }
        if (StringUtils.hasText(actorHeaders.actionReason())) {
            next = next.header("X-Action-Reason", actorHeaders.actionReason().trim());
        }
        if (actorHeaders.callerVendorId() != null) {
            next = next.header("X-Caller-Vendor-Id", String.valueOf(actorHeaders.callerVendorId()));
        }
        return next;
    }

    private ActorHeaders actorHeaders(String userSub, String userRoles, String actionReason, UUID callerVendorId) {
        return new ActorHeaders(userSub, userRoles, actionReason, callerVendorId);
    }

    private String platformStaffPath(UUID id) {
        return PLATFORM_STAFF_PATH_PREFIX + id;
    }

    private String vendorStaffPath(UUID id) {
        return VENDOR_STAFF_PATH_PREFIX + id;
    }

    private <T> T runAccessCall(Supplier<T> action) {
        var retry = retryRegistry.retry("admin-access-client");
        Supplier<T> retryableAction = io.github.resilience4j.retry.Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("admin-access-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new ServiceUnavailableException("Access service unavailable. Try again later.", throwable);
                        })
        );
        return retryableAction.get();
    }

    private void runAccessVoid(Runnable action) {
        runAccessCall(() -> {
            action.run();
            return Boolean.TRUE;
        });
    }

    private record ActorHeaders(
            String userSub,
            String userRoles,
            String actionReason,
            UUID callerVendorId
    ) {
    }
}
