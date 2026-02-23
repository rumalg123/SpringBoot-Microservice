package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PlatformPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpsertPlatformStaffAccessRequest(
        @NotBlank @Size(max = 120) String keycloakUserId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 120) String displayName,
        @NotNull Set<PlatformPermission> permissions,
        Boolean active
) {
}
