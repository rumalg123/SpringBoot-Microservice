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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "platform_staff_access",
        uniqueConstraints = @UniqueConstraint(name = "uk_platform_staff_keycloak", columnNames = "keycloak_user_id"),
        indexes = {
                @Index(name = "idx_platform_staff_active", columnList = "is_active"),
                @Index(name = "idx_platform_staff_deleted", columnList = "is_deleted")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformStaffAccess {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, length = 120)
    private String keycloakUserId;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "platform_staff_permissions", joinColumns = @JoinColumn(name = "staff_access_id"))
    @Column(name = "permission_code", nullable = false, length = 80)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<PlatformPermission> permissions = new LinkedHashSet<>();

    @Column(name = "permission_group_id")
    private UUID permissionGroupId;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "mfa_required", nullable = false)
    @Builder.Default
    private boolean mfaRequired = false;

    @Column(name = "allowed_ips", length = 1000)
    private String allowedIps;

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
