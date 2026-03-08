package com.rumal.vendor_service.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_audit_outbox",
        indexes = {
                @Index(name = "idx_vendor_audit_outbox_pending", columnList = "processed_at, available_at"),
                @Index(name = "idx_vendor_audit_outbox_vendor", columnList = "vendor_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorAuditOutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorLifecycleAction action;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Column(name = "actor_sub", length = 120)
    private String actorSub;

    @Column(name = "actor_tenant_id", length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", length = 1000)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(name = "change_source", nullable = false, length = 30)
    private String changeSource;

    @Column(length = 500)
    private String reason;

    @Column(name = "change_set", columnDefinition = "TEXT")
    private String changeSet;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

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
