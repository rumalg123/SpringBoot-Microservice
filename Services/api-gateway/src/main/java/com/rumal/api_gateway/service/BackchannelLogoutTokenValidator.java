package com.rumal.api_gateway.service;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class BackchannelLogoutTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String LOGOUT_EVENT_CLAIM = "http://schemas.openid.net/event/backchannel-logout";
    private static final OAuth2Error INVALID_LOGOUT_TOKEN = new OAuth2Error("invalid_token", "Invalid backchannel logout token", null);

    private final String clientId;
    private final boolean acceptAzpAsAudience;

    public BackchannelLogoutTokenValidator(String clientId, boolean acceptAzpAsAudience) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.acceptAzpAsAudience = acceptAzpAsAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!hasValidAudience(token)) {
            return OAuth2TokenValidatorResult.failure(INVALID_LOGOUT_TOKEN);
        }
        if (token.hasClaim("nonce")) {
            return OAuth2TokenValidatorResult.failure(INVALID_LOGOUT_TOKEN);
        }
        if (!hasLogoutEvent(token)) {
            return OAuth2TokenValidatorResult.failure(INVALID_LOGOUT_TOKEN);
        }
        if (!StringUtils.hasText(token.getSubject())
                && !StringUtils.hasText(token.getClaimAsString("sid"))
                && !StringUtils.hasText(token.getClaimAsString("session_state"))) {
            return OAuth2TokenValidatorResult.failure(INVALID_LOGOUT_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasValidAudience(Jwt token) {
        if (!StringUtils.hasText(clientId)) {
            return true;
        }
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.stream().anyMatch(clientId::equals)) {
            return true;
        }
        if (acceptAzpAsAudience) {
            String authorizedParty = token.getClaimAsString("azp");
            return clientId.equals(authorizedParty);
        }
        return false;
    }

    private boolean hasLogoutEvent(Jwt token) {
        Object eventsClaim = token.getClaims().get("events");
        if (!(eventsClaim instanceof Map<?, ?> eventsMap)) {
            return false;
        }
        return eventsMap.containsKey(LOGOUT_EVENT_CLAIM);
    }
}
