package com.rumal.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "vendor_order_status_audit", indexes = {
        @Index(name = "idx_vendor_order_status_audit_vo_created", columnList = "vendor_order_id, created_at DESC"),
        @Index(name = "idx_vendor_order_status_audit_source_event", columnList = "source_event_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderStatusAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false, unique = true)
    private UUID sourceEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_order_id", nullable = false)
    private VendorOrder vendorOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "actor_sub", length = 160)
    private String actorSub;

    @Column(name = "actor_tenant_id", updatable = false, length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", length = 500)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 40)
    private String actorType;

    @Column(name = "change_source", nullable = false, length = 40)
    private String changeSource;

    @Column(name = "note", length = 240)
    private String note;

    @Column(name = "change_set", updatable = false, columnDefinition = "TEXT")
    private String changeSet;

    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "request_id", updatable = false, length = 100)
    private String requestId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
