package com.rumal.admin_service.client;

import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Component
@RequiredArgsConstructor
public class PosterClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Qualifier("loadBalancedRestClientBuilder")
    private final RestClient.Builder lbRestClientBuilder;

    public List<Map<String, Object>> listAll(String internalAuth) {
        return getList("/admin/posters", internalAuth);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return getList("/admin/posters/deleted", internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/posters", request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return jsonRequest("PUT", "/admin/posters/" + id, request, internalAuth);
    }

    public void delete(UUID id, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
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
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
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
    }

    public Map<String, Object> generateImageNames(Map<String, Object> request, String internalAuth) {
        return jsonRequest("POST", "/admin/posters/images/names", request, internalAuth);
    }

    public Map<String, Object> uploadImages(List<MultipartFile> files, List<String> keys, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
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
                throw new ServiceUnavailableException("Poster service returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
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
                throw new ServiceUnavailableException("Poster service returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw toDownstreamHttpException(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Poster service unavailable. Try again later.", ex);
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
            message = "Poster service responded with " + ex.getStatusCode().value() + ": " + compactBody;
        } else {
            message = "Poster service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://poster-service" + path);
    }
}
