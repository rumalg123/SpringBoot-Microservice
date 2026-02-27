package com.rumal.api_gateway.controller;

import com.rumal.api_gateway.service.KeycloakManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final KeycloakManagementService keycloakManagementService;

    public AuthController(KeycloakManagementService keycloakManagementService) {
        this.keycloakManagementService = keycloakManagementService;
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(JwtAuthenticationToken authentication) {
        String sessionId = authentication.getToken().getClaimAsString("sid");
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(ResponseEntity.noContent().build());
        }
        return keycloakManagementService.revokeSession(sessionId)
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(e -> Mono.just(ResponseEntity.noContent().build()));
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Void>> resendVerification(JwtAuthenticationToken authentication) {
        String userId = authentication.getToken().getSubject();
        return keycloakManagementService.resendVerificationEmail(userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
