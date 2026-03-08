package com.rumal.api_gateway.controller;

import com.rumal.api_gateway.service.InternalRequestVerificationService;
import com.rumal.api_gateway.service.TokenRevocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/internal/auth/sessions")
public class InternalSessionRevocationController {

    private final InternalRequestVerificationService internalRequestVerificationService;
    private final TokenRevocationService tokenRevocationService;

    public InternalSessionRevocationController(
            InternalRequestVerificationService internalRequestVerificationService,
            TokenRevocationService tokenRevocationService
    ) {
        this.internalRequestVerificationService = internalRequestVerificationService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @DeleteMapping("/by-keycloak-session/{keycloakSessionId}")
    public Mono<ResponseEntity<Void>> revokeByKeycloakSessionId(
            @PathVariable String keycloakSessionId,
            ServerHttpRequest request
    ) {
        internalRequestVerificationService.verify(request);
        if (!StringUtils.hasText(keycloakSessionId)) {
            throw new ResponseStatusException(BAD_REQUEST, "keycloakSessionId is required");
        }
        return tokenRevocationService.revokeSessionHandle(keycloakSessionId.trim(), null)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @DeleteMapping("/by-keycloak-user/{keycloakUserId}")
    public Mono<ResponseEntity<Void>> revokeByKeycloakUserId(
            @PathVariable String keycloakUserId,
            ServerHttpRequest request
    ) {
        internalRequestVerificationService.verify(request);
        if (!StringUtils.hasText(keycloakUserId)) {
            throw new ResponseStatusException(BAD_REQUEST, "keycloakUserId is required");
        }
        return tokenRevocationService.revokeAllSessionsForSubject(keycloakUserId.trim(), null)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
