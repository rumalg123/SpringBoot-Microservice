package com.rumal.api_gateway.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final List<String> requiredAudiences;
    private final boolean acceptAzpAsAudience;

    public AudienceValidator(String audienceConfig, boolean acceptAzpAsAudience) {
        if (!StringUtils.hasText(audienceConfig)) {
            this.requiredAudiences = List.of();
        } else {
            this.requiredAudiences = Arrays.stream(audienceConfig.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        this.acceptAzpAsAudience = acceptAzpAsAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (requiredAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }

        if (jwt.getAudience().stream().anyMatch(requiredAudiences::contains)) {
            return OAuth2TokenValidatorResult.success();
        }

        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
            boolean audiencePresentInResourceAccess = requiredAudiences.stream().anyMatch(resourceAccess::containsKey);
            if (audiencePresentInResourceAccess) {
                return OAuth2TokenValidatorResult.success();
            }
        }

        if (acceptAzpAsAudience) {
            String azp = jwt.getClaimAsString("azp");
            if (StringUtils.hasText(azp) && requiredAudiences.contains(azp.trim())) {
                return OAuth2TokenValidatorResult.success();
            }
        }

        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "Required audience is missing. Expected one of: " + String.join(", ", requiredAudiences),
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
