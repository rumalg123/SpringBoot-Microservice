package com.rumal.api_gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
public class BackchannelLogoutService {

    private final ReactiveJwtDecoder logoutTokenDecoder;
    private final SessionHandleResolver sessionHandleResolver;

    public BackchannelLogoutService(
            SessionHandleResolver sessionHandleResolver,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.jwk-set-uri:}") String jwkSetUri,
            @Value("${keycloak.backchannel.client-id:}") String backchannelClientId,
            @Value("${keycloak.accept-azp-as-audience:false}") boolean acceptAzpAsAudience
    ) {
        this.sessionHandleResolver = sessionHandleResolver;
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(resolveJwkSetUri(issuerUri, jwkSetUri))
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> logoutValidator = new BackchannelLogoutTokenValidator(backchannelClientId, acceptAzpAsAudience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, logoutValidator));
        this.logoutTokenDecoder = decoder;
    }

    public Mono<LogoutContext> parseLogoutToken(String logoutToken) {
        if (!StringUtils.hasText(logoutToken)) {
            return Mono.error(new IllegalArgumentException("logout_token is required"));
        }
        return logoutTokenDecoder.decode(logoutToken.trim())
                .map(jwt -> new LogoutContext(
                        jwt.getSubject() == null ? "" : jwt.getSubject().trim(),
                        sessionHandleResolver.extractKeycloakSessionId(jwt),
                        jwt.getExpiresAt()
                ));
    }

    private String resolveJwkSetUri(String issuerUri, String configuredJwkSetUri) {
        if (StringUtils.hasText(configuredJwkSetUri)) {
            return configuredJwkSetUri.trim();
        }
        String normalizedIssuer = issuerUri == null ? "" : issuerUri.trim();
        if (!StringUtils.hasText(normalizedIssuer)) {
            throw new IllegalArgumentException("spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
        }
        if (normalizedIssuer.endsWith("/")) {
            normalizedIssuer = normalizedIssuer.substring(0, normalizedIssuer.length() - 1);
        }
        return normalizedIssuer + "/protocol/openid-connect/certs";
    }

    public record LogoutContext(
            String subject,
            String sessionHandle,
            java.time.Instant expiresAt
    ) {
    }
}
