package com.rumal.access_service.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_staff_access",
        uniqueConstraints = @UniqueConstraint(name = "uk_vendor_staff_vendor_keycloak", columnNames = {"vendor_id", "keycloak_user_id"}),
        indexes = {
                @Index(name = "idx_vendor_staff_vendor", columnList = "vendor_id"),
                @Index(name = "idx_vendor_staff_keycloak", columnList = "keycloak_user_id"),
                @Index(name = "idx_vendor_staff_active", columnList = "is_active"),
                @Index(name = "idx_vendor_staff_expiry", columnList = "is_active, is_deleted, access_expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorStaffAccess {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "keycloak_user_id", nullable = false, length = 120)
    private String keycloakUserId;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vendor_staff_permissions", joinColumns = @JoinColumn(name = "staff_access_id"))
    @Column(name = "permission_code", nullable = false, length = 80)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<VendorPermission> permissions = new LinkedHashSet<>();

    @Column(name = "permission_group_id")
    private UUID permissionGroupId;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    @Column(name = "allowed_ips", length = 1000)
    private String allowedIps;

    @Version
    private Long version;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
