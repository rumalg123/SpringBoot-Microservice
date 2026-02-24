package com.rumal.access_service.controller;

import com.rumal.access_service.dto.ApiKeyResponse;
import com.rumal.access_service.dto.CreateApiKeyRequest;
import com.rumal.access_service.dto.CreateApiKeyResponse;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateApiKeyResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.createApiKey(request);
    }

    @GetMapping("/by-keycloak/{keycloakId}")
    public List<ApiKeyResponse> listByKeycloakId(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listApiKeys(keycloakId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.deleteApiKey(id);
    }
}
