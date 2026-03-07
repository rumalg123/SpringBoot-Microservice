package com.rumal.api_gateway.controller;

import com.rumal.api_gateway.service.AccessSessionService;
import com.rumal.api_gateway.service.KeycloakManagementService;
import com.rumal.api_gateway.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakManagementService keycloakManagementService;
    private final AccessSessionService accessSessionService;
    private final TokenRevocationService tokenRevocationService;

    public AuthController(
            KeycloakManagementService keycloakManagementService,
            AccessSessionService accessSessionService,
            TokenRevocationService tokenRevocationService
    ) {
        this.keycloakManagementService = keycloakManagementService;
        this.accessSessionService = accessSessionService;
        this.tokenRevocationService = tokenRevocationService;
    }

    // H-16: Return 503 when session revocation fails so the client knows logout may not have succeeded
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(JwtAuthenticationToken authentication) {
        String sessionHandle = extractSessionHandle(authentication.getToken());
        String keycloakSessionId = extractKeycloakSessionId(authentication.getToken());
        return Mono.whenDelayError(
                        tokenRevocationService.revokeToken(
                                authentication.getToken().getTokenValue(),
                                authentication.getToken().getExpiresAt()
                        ),
                        accessSessionService.revokeSession(sessionHandle),
                        StringUtils.hasText(keycloakSessionId)
                                ? keycloakManagementService.revokeSession(keycloakSessionId)
                                : Mono.empty()
                )
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(e -> {
                    log.error("Failed to complete logout for userSub={} sessionHandle={} keycloakSessionId={}: {}",
                            authentication.getToken().getSubject(), sessionHandle, keycloakSessionId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(503).<Void>build());
                });
    }

    @PostMapping("/session")
    public Mono<ResponseEntity<Void>> syncSession(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        String userId = authentication.getToken().getSubject();
        String sessionHandle = extractSessionHandle(authentication.getToken());
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionHandle)) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }
        return accessSessionService.syncSession(userId, sessionHandle, exchange)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Void>> resendVerification(JwtAuthenticationToken authentication) {
        String userId = authentication.getToken().getSubject();
        return keycloakManagementService.resendVerificationEmail(userId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    private String extractKeycloakSessionId(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (StringUtils.hasText(sessionId)) {
            return sessionId.trim();
        }
        String sessionState = jwt.getClaimAsString("session_state");
        if (StringUtils.hasText(sessionState)) {
            return sessionState.trim();
        }
        return "";
    }

    private String extractSessionHandle(Jwt jwt) {
        String keycloakSessionId = extractKeycloakSessionId(jwt);
        if (StringUtils.hasText(keycloakSessionId)) {
            return keycloakSessionId;
        }
        String jwtId = jwt.getId();
        if (StringUtils.hasText(jwtId)) {
            return jwtId.trim();
        }
        String jti = jwt.getClaimAsString("jti");
        if (StringUtils.hasText(jti)) {
            return jti.trim();
        }
        return "";
    }
}
