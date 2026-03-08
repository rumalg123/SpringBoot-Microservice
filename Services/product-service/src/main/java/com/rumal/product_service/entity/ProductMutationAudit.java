package com.rumal.product_service.entity;

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
        name = "product_mutation_audit",
        indexes = {
                @Index(name = "idx_product_mutation_audit_product_created", columnList = "product_id, created_at DESC"),
                @Index(name = "idx_product_mutation_audit_vendor_created", columnList = "vendor_id, created_at DESC"),
                @Index(name = "idx_product_mutation_audit_source_event", columnList = "source_event_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMutationAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "vendor_id", updatable = false)
    private UUID vendorId;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "actor_sub", updatable = false, length = 120)
    private String actorSub;

    @Column(name = "actor_tenant_id", updatable = false, length = 120)
    private String actorTenantId;

    @Column(name = "actor_roles", updatable = false, length = 1000)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 30)
    private String actorType;

    @Column(name = "change_source", nullable = false, updatable = false, length = 40)
    private String changeSource;

    @Column(updatable = false, length = 500)
    private String details;

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
}
