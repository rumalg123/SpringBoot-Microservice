package com.rumal.admin_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "admin_audit_outbox",
        indexes = {
                @Index(name = "idx_admin_audit_outbox_pending", columnList = "processed_at, available_at"),
                @Index(name = "idx_admin_audit_outbox_action", columnList = "action, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditOutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_keycloak_id", length = 120)
    private String actorKeycloakId;

    @Column(name = "actor_tenant_id", length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", length = 500)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Column(name = "change_source", nullable = false, length = 40)
    private String changeSource;

    @Column(length = 2000)
    private String details;

    @Column(name = "change_set", columnDefinition = "TEXT")
    private String changeSet;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
