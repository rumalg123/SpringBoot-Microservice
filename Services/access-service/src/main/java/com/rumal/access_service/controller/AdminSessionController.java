package com.rumal.access_service.controller;

import com.rumal.access_service.dto.ActiveSessionResponse;
import com.rumal.access_service.dto.RegisterSessionRequest;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@RestController
@RequestMapping("/admin/sessions")
@RequiredArgsConstructor
public class AdminSessionController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActiveSessionResponse register(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @Valid @RequestBody RegisterSessionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.registerSession(request);
    }

    @GetMapping("/by-keycloak/{keycloakId}")
    public Page<ActiveSessionResponse> listByKeycloakId(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakId,
            Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listSessionsByKeycloakId(keycloakId, pageable);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID sessionId
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.revokeSession(sessionId);
    }

    @DeleteMapping("/by-keycloak/{keycloakId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakId
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.revokeAllSessions(keycloakId);
    }
}
