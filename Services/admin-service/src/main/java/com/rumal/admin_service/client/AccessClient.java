package com.rumal.admin_service.client;

import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
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

    private final RestClient.Builder lbRestClientBuilder;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public AccessClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public Map<String, Object> getPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getMap("/internal/access/platform/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listVendorStaffAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return getList("/internal/access/vendors/by-keycloak/" + keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listPlatformStaff(String internalAuth) {
        return getList("/admin/platform-staff", internalAuth);
    }

    public List<Map<String, Object>> listDeletedPlatformStaff(String internalAuth) {
        return getList("/admin/platform-staff/deleted", internalAuth);
    }

    public Map<String, Object> getPlatformStaffById(UUID id, String internalAuth) {
        return getMap("/admin/platform-staff/" + id, internalAuth);
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/platform-staff", request, internalAuth);
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("POST", "/admin/platform-staff", request, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/platform-staff/" + id, request, internalAuth);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("PUT", "/admin/platform-staff/" + id, request, internalAuth, userSub, userRoles, actionReason);
    }

    public void deletePlatformStaff(UUID id, String internalAuth) {
        deleteNoContent("/admin/platform-staff/" + id, internalAuth);
    }

    public void deletePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        deleteNoContent("/admin/platform-staff/" + id, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth) {
        return postNoBody("/admin/platform-staff/" + id + "/restore", internalAuth);
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return postNoBody("/admin/platform-staff/" + id + "/restore", internalAuth, userSub, userRoles, actionReason);
    }

    public List<Map<String, Object>> listVendorStaff(UUID vendorId, String internalAuth) {
        String path = vendorId == null ? "/admin/vendor-staff" : "/admin/vendor-staff?vendorId=" + vendorId;
        return getList(path, internalAuth);
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth) {
        return getList("/admin/vendor-staff/deleted", internalAuth);
    }

    public Map<String, Object> listAccessAudit(
            String targetType,
            UUID targetId,
            UUID vendorId,
            String action,
            String actorQuery,
            String from,
            String to,
            Integer page,
            Integer size,
            Integer limit,
            String internalAuth
    ) {
        StringBuilder path = new StringBuilder("/admin/access-audit");
        String sep = "?";
        if (StringUtils.hasText(targetType)) {
            path.append(sep).append("targetType=").append(targetType.trim());
            sep = "&";
        }
        if (targetId != null) {
            path.append(sep).append("targetId=").append(targetId);
            sep = "&";
        }
        if (vendorId != null) {
            path.append(sep).append("vendorId=").append(vendorId);
            sep = "&";
        }
        if (StringUtils.hasText(action)) {
            path.append(sep).append("action=").append(action.trim());
            sep = "&";
        }
        if (StringUtils.hasText(actorQuery)) {
            path.append(sep).append("actorQuery=").append(java.net.URLEncoder.encode(actorQuery.trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (StringUtils.hasText(from)) {
            path.append(sep).append("from=").append(java.net.URLEncoder.encode(from.trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (StringUtils.hasText(to)) {
            path.append(sep).append("to=").append(java.net.URLEncoder.encode(to.trim(), java.nio.charset.StandardCharsets.UTF_8));
            sep = "&";
        }
        if (page != null) {
            path.append(sep).append("page=").append(page);
            sep = "&";
        }
        if (size != null) {
            path.append(sep).append("size=").append(size);
            sep = "&";
        }
        if (limit != null) {
            path.append(sep).append("limit=").append(limit);
        }
        return getMap(path.toString(), internalAuth);
    }

    public Map<String, Object> getVendorStaffById(UUID id, String internalAuth) {
        return getMap("/admin/vendor-staff/" + id, internalAuth);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/vendor-staff", request, internalAuth);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("POST", "/admin/vendor-staff", request, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/vendor-staff/" + id, request, internalAuth);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return jsonRequest("PUT", "/admin/vendor-staff/" + id, request, internalAuth, userSub, userRoles, actionReason);
    }

    public void deleteVendorStaff(UUID id, String internalAuth) {
        deleteNoContent("/admin/vendor-staff/" + id, internalAuth);
    }

    public void deleteVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        deleteNoContent("/admin/vendor-staff/" + id, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth) {
        return postNoBody("/admin/vendor-staff/" + id + "/restore", internalAuth);
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return postNoBody("/admin/vendor-staff/" + id + "/restore", internalAuth, userSub, userRoles, actionReason);
    }

    private List<Map<String, Object>> getList(String path, String internalAuth) {
        return runAccessCall(() -> {
            RestClient rc = lbRestClientBuilder.build();
            try {
                List<Map<String, Object>> response = rc.get().uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth)
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
        return runAccessCall(() -> {
            RestClient rc = lbRestClientBuilder.build();
            try {
                Map<String, Object> response = rc.get().uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth)
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
        return jsonRequest(method, path, request, internalAuth, null, null, null);
    }

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return runAccessCall(() -> {
            RestClient rc = lbRestClientBuilder.build();
            try {
                RestClient.RequestBodySpec spec = switch (method) {
                    case "POST" -> rc.post().uri(buildUri(path));
                    case "PUT" -> rc.put().uri(buildUri(path));
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };
                Map<String, Object> response = ((RestClient.RequestBodySpec) applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles, actionReason))
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
        deleteNoContent(path, internalAuth, null, null, null);
    }

    private void deleteNoContent(String path, String internalAuth, String userSub, String userRoles, String actionReason) {
        runAccessVoid(() -> {
            RestClient rc = lbRestClientBuilder.build();
            try {
                applyActorHeaders(rc.delete().uri(buildUri(path)).header("X-Internal-Auth", internalAuth), userSub, userRoles, actionReason)
                        .retrieve().toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw toDownstream(ex);
            } catch (RestClientException | IllegalStateException ex) {
                throw new ServiceUnavailableException("Access service unavailable. Try again later.", ex);
            }
        });
    }

    private Map<String, Object> postNoBody(String path, String internalAuth) {
        return postNoBody(path, internalAuth, null, null, null);
    }

    private Map<String, Object> postNoBody(String path, String internalAuth, String userSub, String userRoles, String actionReason) {
        return runAccessCall(() -> {
            RestClient rc = lbRestClientBuilder.build();
            try {
                Map<String, Object> response = applyActorHeaders(rc.post().uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles, actionReason)
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

    private RestClient.RequestHeadersSpec<?> applyActorHeaders(
            RestClient.RequestHeadersSpec<?> spec,
            String userSub,
            String userRoles,
            String actionReason
    ) {
        RestClient.RequestHeadersSpec<?> next = spec;
        if (StringUtils.hasText(userSub)) {
            next = next.header("X-User-Sub", userSub);
        }
        if (StringUtils.hasText(userRoles)) {
            next = next.header("X-User-Roles", userRoles);
        }
        if (StringUtils.hasText(actionReason)) {
            next = next.header("X-Action-Reason", actionReason.trim());
        }
        return next;
    }

    private <T> T runAccessCall(Supplier<T> action) {
        return circuitBreakerFactory.create("admin-access-client")
                .run(action::get, throwable -> {
                    if (throwable instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new ServiceUnavailableException("Access service unavailable. Try again later.", throwable);
                });
    }

    private void runAccessVoid(Runnable action) {
        runAccessCall(() -> {
            action.run();
            return Boolean.TRUE;
        });
    }
}
