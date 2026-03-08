package com.rumal.order_service.entity;

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
        name = "order_status_audit_outbox",
        indexes = {
                @Index(name = "idx_order_status_audit_outbox_pending", columnList = "processed_at, available_at"),
                @Index(name = "idx_order_status_audit_outbox_scope", columnList = "audit_scope, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusAuditOutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "audit_scope", nullable = false, length = 20)
    private String auditScope;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "vendor_order_id")
    private UUID vendorOrderId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "actor_sub", length = 160)
    private String actorSub;

    @Column(name = "actor_tenant_id", length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", length = 500)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 40)
    private String actorType;

    @Column(name = "change_source", nullable = false, length = 40)
    private String changeSource;

    @Column(name = "note", length = 240)
    private String note;

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
