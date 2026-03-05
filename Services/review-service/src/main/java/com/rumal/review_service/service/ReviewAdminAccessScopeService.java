package com.rumal.review_service.service;

import com.rumal.review_service.client.AccessClient;
import com.rumal.review_service.dto.PlatformAccessLookupResponse;
import com.rumal.review_service.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewAdminAccessScopeService {

    public static final String PLATFORM_REVIEWS_MANAGE = "platform.reviews.manage";

    private final AccessClient accessClient;

    public void assertCanManageReviews(String userSub, String userRoles, String internalAuth) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin")) {
            return;
        }
        if (!roles.contains("platform_staff")) {
            throw new UnauthorizedException("Caller does not have review admin access");
        }
        PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
        Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
        if (!platformAccess.active() || !permissions.contains(PLATFORM_REVIEWS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have review management permission");
        }
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : rolesHeader.split(",")) {
            String normalized = normalizeRole(role);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
                if ("platform_admin".equals(normalized)) {
                    roles.add("super_admin");
                }
            }
        }
        return Set.copyOf(roles);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring("role_".length());
        } else if (normalized.startsWith("role-")) {
            normalized = normalized.substring("role-".length());
        } else if (normalized.startsWith("role:")) {
            normalized = normalized.substring("role:".length());
        }
        return normalized.replace('-', '_').replace(' ', '_');
    }
}
