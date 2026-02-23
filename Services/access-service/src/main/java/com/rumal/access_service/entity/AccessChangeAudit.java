package com.rumal.access_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "access_change_audit",
        indexes = {
                @Index(name = "idx_access_audit_target", columnList = "target_type,target_id"),
                @Index(name = "idx_access_audit_vendor", columnList = "vendor_id"),
                @Index(name = "idx_access_audit_keycloak", columnList = "keycloak_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessChangeAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "keycloak_user_id", length = 120)
    private String keycloakUserId;

    @Column(name = "email", length = 180)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AccessChangeAction action;

    @Column(name = "active_after", nullable = false)
    private boolean activeAfter;

    @Column(name = "deleted_after", nullable = false)
    private boolean deletedAfter;

    @Column(name = "permissions_snapshot", length = 1000)
    private String permissionsSnapshot;

    @Column(name = "actor_sub", length = 120)
    private String actorSub;

    @Column(name = "actor_roles", length = 500)
    private String actorRoles;

    @Column(name = "actor_type", length = 20)
    private String actorType;

    @Column(name = "change_source", length = 40)
    private String changeSource;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

