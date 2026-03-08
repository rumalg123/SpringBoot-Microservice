package com.rumal.vendor_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
        name = "vendor_lifecycle_audit",
        indexes = {
                @Index(name = "idx_vendor_lifecycle_vendor", columnList = "vendor_id"),
                @Index(name = "idx_vendor_lifecycle_created", columnList = "created_at"),
                @Index(name = "idx_vendor_lifecycle_action", columnList = "action"),
                @Index(name = "idx_vendor_lifecycle_source_event", columnList = "source_event_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorLifecycleAudit {

    @Id
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false, unique = true)
    private UUID sourceEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private VendorLifecycleAction action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 120)
    private String resourceId;

    @Column(name = "actor_sub", updatable = false, length = 120)
    private String actorSub;

    @Column(name = "actor_tenant_id", updatable = false, length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", updatable = false, length = 1000)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 30)
    private String actorType;

    @Column(name = "change_source", nullable = false, updatable = false, length = 30)
    private String changeSource;

    @Column(updatable = false, length = 500)
    private String reason;

    @Column(name = "change_set", updatable = false, columnDefinition = "TEXT")
    private String changeSet;

    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "request_id", updatable = false, length = 100)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
