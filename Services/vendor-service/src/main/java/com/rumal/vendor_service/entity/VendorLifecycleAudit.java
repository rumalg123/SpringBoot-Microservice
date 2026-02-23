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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_lifecycle_audit",
        indexes = {
                @Index(name = "idx_vendor_lifecycle_vendor", columnList = "vendor_id"),
                @Index(name = "idx_vendor_lifecycle_created", columnList = "created_at"),
                @Index(name = "idx_vendor_lifecycle_action", columnList = "action")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VendorLifecycleAction action;

    @Column(name = "actor_sub", length = 120)
    private String actorSub;

    @Column(name = "actor_roles", length = 1000)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(name = "change_source", nullable = false, length = 30)
    private String changeSource;

    @Column(length = 500)
    private String reason;

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

