package com.rumal.api_gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class SessionHandleResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public SessionHandleResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractKeycloakSessionId(Jwt jwt) {
        if (jwt == null) {
            return "";
        }
        return extractKeycloakSessionId(jwt.getClaims());
    }

    public String extractKeycloakSessionId(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return "";
        }
        String sid = asTrimmedString(claims.get("sid"));
        if (StringUtils.hasText(sid)) {
            return sid;
        }
        String sessionState = asTrimmedString(claims.get("session_state"));
        if (StringUtils.hasText(sessionState)) {
            return sessionState;
        }
        return "";
    }

    public String extractSessionHandle(Jwt jwt) {
        if (jwt == null) {
            return "";
        }
        String keycloakSessionId = extractKeycloakSessionId(jwt);
        if (StringUtils.hasText(keycloakSessionId)) {
            return keycloakSessionId;
        }
        if (StringUtils.hasText(jwt.getId())) {
            return jwt.getId().trim();
        }
        String jti = jwt.getClaimAsString("jti");
        if (StringUtils.hasText(jti)) {
            return jti.trim();
        }
        return "";
    }

    public String extractSessionHandle(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return "";
        }
        String keycloakSessionId = extractKeycloakSessionId(claims);
        if (StringUtils.hasText(keycloakSessionId)) {
            return keycloakSessionId;
        }
        String jti = asTrimmedString(claims.get("jti"));
        if (StringUtils.hasText(jti)) {
            return jti;
        }
        return "";
    }

    public String extractSubject(Map<String, Object> claims) {
        return asTrimmedString(claims == null ? null : claims.get("sub"));
    }

    public Map<String, Object> parseUnverifiedClaims(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return Map.of();
        }
        String[] parts = rawToken.trim().split("\\.");
        if (parts.length < 2) {
            return Map.of();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return "";
        }
        String converted = value instanceof String ? ((String) value).trim() : String.valueOf(value).trim();
        return converted;
    }
}
