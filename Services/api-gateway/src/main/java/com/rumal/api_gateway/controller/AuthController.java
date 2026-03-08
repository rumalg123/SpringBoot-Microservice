package com.rumal.api_gateway.controller;

import com.rumal.api_gateway.config.TrustedProxyResolver;
import com.rumal.api_gateway.service.AccessSessionService;
import com.rumal.api_gateway.service.BackchannelLogoutService;
import com.rumal.api_gateway.service.KeycloakManagementService;
import com.rumal.api_gateway.service.SessionHandleResolver;
import com.rumal.api_gateway.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final KeycloakManagementService keycloakManagementService;
    private final AccessSessionService accessSessionService;
    private final TokenRevocationService tokenRevocationService;
    private final SessionHandleResolver sessionHandleResolver;
    private final TrustedProxyResolver trustedProxyResolver;
    private final BackchannelLogoutService backchannelLogoutService;

    public AuthController(
            KeycloakManagementService keycloakManagementService,
            AccessSessionService accessSessionService,
            TokenRevocationService tokenRevocationService,
            SessionHandleResolver sessionHandleResolver,
            TrustedProxyResolver trustedProxyResolver,
            BackchannelLogoutService backchannelLogoutService
    ) {
        this.keycloakManagementService = keycloakManagementService;
        this.accessSessionService = accessSessionService;
        this.tokenRevocationService = tokenRevocationService;
        this.sessionHandleResolver = sessionHandleResolver;
        this.trustedProxyResolver = trustedProxyResolver;
        this.backchannelLogoutService = backchannelLogoutService;
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(JwtAuthenticationToken authentication, ServerWebExchange exchange) {
        String userId = authentication.getToken().getSubject();
        String sessionHandle = sessionHandleResolver.extractSessionHandle(authentication.getToken());
        String keycloakSessionId = sessionHandleResolver.extractKeycloakSessionId(authentication.getToken());
        String clientIp = trustedProxyResolver.resolveClientIp(exchange);
        String userAgent = normalizeUserAgent(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));

        return Mono.whenDelayError(
                        tokenRevocationService.revokeToken(
                                authentication.getToken().getTokenValue(),
                                authentication.getToken().getExpiresAt()
                        ),
                        tokenRevocationService.revokeSessionHandle(sessionHandle, authentication.getToken().getExpiresAt()),
                        accessSessionService.revokeSession(sessionHandle),
                        StringUtils.hasText(keycloakSessionId)
                                ? keycloakManagementService.revokeSession(keycloakSessionId)
                                : Mono.empty()
                )
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(error -> {
                    log.error(
                            "Failed to complete logout for userSub={} sessionHandle={} keycloakSessionId={} clientIp={} userAgent={}: {}",
                            userId,
                            sessionHandle,
                            keycloakSessionId,
                            clientIp,
                            userAgent,
                            error.getMessage(),
                            error
                    );
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<Void>build());
                });
    }

    @PostMapping("/session")
    public Mono<ResponseEntity<Void>> syncSession(
            JwtAuthenticationToken authentication,
            ServerWebExchange exchange
    ) {
        String userId = authentication.getToken().getSubject();
        String sessionHandle = sessionHandleResolver.extractSessionHandle(authentication.getToken());
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionHandle)) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }

        String clientIp = trustedProxyResolver.resolveClientIp(exchange);
        String userAgent = normalizeUserAgent(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));

        return Mono.zip(
                        tokenRevocationService.isSubjectRevoked(userId),
                        tokenRevocationService.isSessionHandleRevoked(sessionHandle)
                )
                .flatMap(revocationState -> {
                    boolean subjectRevoked = Boolean.TRUE.equals(revocationState.getT1());
                    boolean sessionRevoked = Boolean.TRUE.equals(revocationState.getT2());
                    if (subjectRevoked || sessionRevoked) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build());
                    }
                    return accessSessionService.syncSession(userId, sessionHandle, exchange)
                            .then(tokenRevocationService.registerActiveSession(
                                    sessionHandle,
                                    userId,
                                    authentication.getToken().getExpiresAt(),
                                    clientIp,
                                    userAgent
                            ))
                            .thenReturn(ResponseEntity.noContent().<Void>build());
                })
                .onErrorResume(IllegalStateException.class, ignored ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build()));
    }

    @PostMapping("/backchannel-logout")
    public Mono<ResponseEntity<Void>> backchannelLogout(@RequestParam("logout_token") String logoutToken) {
        return backchannelLogoutService.parseLogoutToken(logoutToken)
                .flatMap(context -> {
                    if (StringUtils.hasText(context.sessionHandle())) {
                        return Mono.whenDelayError(
                                        tokenRevocationService.revokeSessionHandle(context.sessionHandle(), context.expiresAt()),
                                        accessSessionService.revokeSession(context.sessionHandle())
                                )
                                .thenReturn(ResponseEntity.ok().<Void>build());
                    }
                    return Mono.whenDelayError(
                                    tokenRevocationService.revokeAllSessionsForSubject(context.subject(), context.expiresAt()),
                                    accessSessionService.revokeAllSessions(context.subject())
                            )
                            .thenReturn(ResponseEntity.ok().<Void>build());
                })
                .onErrorResume(IllegalArgumentException.class, error -> {
                    log.warn("Rejected backchannel logout request: {}", error.getMessage());
                    return Mono.just(ResponseEntity.badRequest().<Void>build());
                })
                .onErrorResume(error -> {
                    log.warn("Failed to process backchannel logout: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build());
                });
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Void>> resendVerification(JwtAuthenticationToken authentication) {
        String userId = authentication.getToken().getSubject();
        return keycloakManagementService.resendVerificationEmail(userId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    private String normalizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "";
        }
        String normalized = userAgent.trim().replaceAll("[\\r\\n]+", " ");
        if (normalized.length() <= MAX_USER_AGENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
