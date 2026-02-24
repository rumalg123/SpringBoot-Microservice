package com.rumal.access_service.dto;

import com.rumal.access_service.entity.VendorPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UpsertVendorStaffAccessRequest(
        @NotNull UUID vendorId,
        @NotBlank @Size(max = 120) String keycloakUserId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 120) String displayName,
        @NotNull Set<VendorPermission> permissions,
        Boolean active,
        UUID permissionGroupId,
        Boolean mfaRequired,
        Instant accessExpiresAt,
        @Size(max = 1000) String allowedIps
) {
}
