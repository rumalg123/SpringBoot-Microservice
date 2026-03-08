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
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(
        name = "admin_audit_log",
        indexes = {
                @Index(name = "idx_audit_actor_keycloak_id", columnList = "actor_keycloak_id"),
                @Index(name = "idx_audit_action", columnList = "action"),
                @Index(name = "idx_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_source_event_id", columnList = "source_event_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "actor_keycloak_id", nullable = false, updatable = false, length = 120)
    private String actorKeycloakId;

    @Column(name = "actor_tenant_id", updatable = false, length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", updatable = false, length = 500)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private String actorType;

    @Column(nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "resource_type", updatable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 120)
    private String resourceId;

    @Column(name = "change_source", nullable = false, updatable = false, length = 40)
    private String changeSource;

    @Column(updatable = false, length = 2000)
    private String details;

    @Column(name = "change_set", updatable = false, columnDefinition = "TEXT")
    private String changeSet;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "request_id", updatable = false, length = 100)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
