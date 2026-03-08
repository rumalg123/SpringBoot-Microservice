package com.rumal.admin_service.client;

import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
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
public class PosterClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Qualifier("loadBalancedRestClientBuilder")
    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    public PosterClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
    }

    public List<Map<String, Object>> listAll(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList("/admin/posters", internalAuth, userSub, userRoles);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth, String userSub, String userRoles) {
        return getPagedContentList("/admin/posters/deleted", internalAuth, userSub, userRoles);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest("POST", "/admin/posters", request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return jsonRequest("PUT", "/admin/posters/" + id, request, internalAuth, userSub, userRoles);
    }

    public void delete(UUID id, String internalAuth, String userSub, String userRoles) {
        runPosterVoid(() -> {
            RestClient rc = restClient;
            try {
                applyActorHeaders(rc.delete()
                        .uri(buildUri("/admin/posters/" + id))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    public Map<String, Object> restore(UUID id, String internalAuth, String userSub, String userRoles) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> body = applyActorHeaders(rc.post()
                        .uri(buildUri("/admin/posters/" + id + "/restore"))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles)
                        .retrieve()
                        .body(MAP_TYPE);
                if (body == null) {
                    throw new ServiceUnavailableException("Poster service returned an empty response", null);
                }
                return body;
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    public Map<String, Object> prepareImageUploads(
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        return jsonRequest("POST", "/admin/posters/images/presign", request, internalAuth, userSub, userRoles);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(
            String path,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
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

    private Map<String, Object> getMap(String path, String internalAuth, String userSub, String userRoles) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = applyActorHeaders(rc.get()
                        .uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles)
                        .retrieve()
                        .body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Poster service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    private List<Map<String, Object>> getList(String path, String internalAuth, String userSub, String userRoles) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                List<Map<String, Object>> response = applyActorHeaders(rc.get()
                        .uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth), userSub, userRoles)
                        .retrieve()
                        .body(LIST_MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Poster service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    private Map<String, Object> jsonRequest(
            String method,
            String path,
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec spec = switch (method) {
                    case "POST" -> rc.post().uri(buildUri(path));
                    case "PUT" -> rc.put().uri(buildUri(path));
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };
                spec = applyActorHeaders(spec.header("X-Internal-Auth", internalAuth), userSub, userRoles);
                Map<String, Object> response = spec
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Poster service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    private DownstreamHttpException toDownstreamHttpException(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String message;
        if (StringUtils.hasText(body)) {
            String compactBody = body.replaceAll("\\s+", " ").trim();
            if (compactBody.length() > 300) {
                compactBody = compactBody.substring(0, 300) + "...";
            }
            message = "Poster service responded with " + ex.getStatusCode().value() + ": " + compactBody;
        } else {
            message = "Poster service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://poster-service" + path);
    }

    private RestClient.RequestBodySpec applyActorHeaders(
            RestClient.RequestBodySpec spec,
            String userSub,
            String userRoles
    ) {
        RestClient.RequestBodySpec next = spec;
        if (StringUtils.hasText(userSub)) {
            next = next.header("X-User-Sub", userSub);
        }
        if (StringUtils.hasText(userRoles)) {
            next = next.header("X-User-Roles", userRoles);
        }
        return next;
    }

    private RestClient.RequestHeadersSpec<?> applyActorHeaders(
            RestClient.RequestHeadersSpec<?> spec,
            String userSub,
            String userRoles
    ) {
        RestClient.RequestHeadersSpec<?> next = spec;
        if (StringUtils.hasText(userSub)) {
            next = next.header("X-User-Sub", userSub);
        }
        if (StringUtils.hasText(userRoles)) {
            next = next.header("X-User-Roles", userRoles);
        }
        return next;
    }

    private <T> T runPosterCall(Supplier<T> action) {
        var retry = retryRegistry.retry("admin-poster-client");
        Supplier<T> retryableAction = io.github.resilience4j.retry.Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("admin-poster-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new ServiceUnavailableException("Poster service unavailable. Try again later.", throwable);
                        })
        );
        return retryableAction.get();
    }

    private void runPosterVoid(Runnable action) {
        runPosterCall(() -> {
            action.run();
            return Boolean.TRUE;
        });
    }
}
