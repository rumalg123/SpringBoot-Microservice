package com.rumal.admin_service.client;

import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
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

    public List<Map<String, Object>> listAll(String internalAuth) {
        return getPagedContentList("/admin/posters", internalAuth);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return getPagedContentList("/admin/posters/deleted", internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/posters", request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/posters/" + id, request, internalAuth);
    }

    public void delete(UUID id, String internalAuth) {
        runPosterVoid(() -> {
            RestClient rc = restClient;
            try {
                rc.delete()
                        .uri(buildUri("/admin/posters/" + id))
                        .header("X-Internal-Auth", internalAuth)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> body = rc.post()
                        .uri(buildUri("/admin/posters/" + id + "/restore"))
                        .header("X-Internal-Auth", internalAuth)
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

    public Map<String, Object> generateImageNames(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/posters/images/names", request, internalAuth);
    }

    public Map<String, Object> uploadImages(List<MultipartFile> files, List<String> keys, String internalAuth) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
                for (MultipartFile file : files) {
                    HttpHeaders partHeaders = new HttpHeaders();
                    partHeaders.setContentType(MediaType.parseMediaType(
                            file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE
                    ));
                    ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            return file.getOriginalFilename();
                        }
                    };
                    parts.add("files", new HttpEntity<>(resource, partHeaders));
                }
                if (keys != null) {
                    for (String key : keys) {
                        if (key != null) {
                            parts.add("keys", key);
                        }
                    }
                }

                Map<String, Object> response = rc.post()
                        .uri(buildUri("/admin/posters/images"))
                        .header("X-Internal-Auth", internalAuth)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(MAP_TYPE);
                if (response == null) {
                    throw new ServiceUnavailableException("Poster service returned an empty response", null);
                }
                return response;
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (IOException | RestClientException ex) {
                throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPagedContentList(String path, String internalAuth) {
        Map<String, Object> page = getMap(path, internalAuth);
        Object content = page.get("content");
        if (content instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> getMap(String path, String internalAuth) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> response = rc.get()
                        .uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth)
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

    private List<Map<String, Object>> getList(String path, String internalAuth) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
            try {
                List<Map<String, Object>> response = rc.get()
                        .uri(buildUri(path))
                        .header("X-Internal-Auth", internalAuth)
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

    private Map<String, Object> jsonRequest(String method, String path, Map<String, Object> request, String internalAuth) {
        return runPosterCall(() -> {
            RestClient rc = restClient;
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
