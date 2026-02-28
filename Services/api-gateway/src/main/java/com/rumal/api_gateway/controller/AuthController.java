package com.rumal.api_gateway.controller;

import com.rumal.api_gateway.service.KeycloakManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakManagementService keycloakManagementService;

    public AuthController(KeycloakManagementService keycloakManagementService) {
        this.keycloakManagementService = keycloakManagementService;
    }

    // H-16: Return 503 when session revocation fails so the client knows logout may not have succeeded
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(JwtAuthenticationToken authentication) {
        String sessionId = authentication.getToken().getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(ResponseEntity.noContent().build());
        }
        return keycloakManagementService.revokeSession(sessionId)
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(e -> {
                    log.error("Failed to revoke Keycloak session {}: {}", sessionId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(503).<Void>build());
                });
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Void>> resendVerification(JwtAuthenticationToken authentication) {
        String userId = authentication.getToken().getSubject();
        return keycloakManagementService.resendVerificationEmail(userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
