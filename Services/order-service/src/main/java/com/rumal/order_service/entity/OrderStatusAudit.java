package com.rumal.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_audit", indexes = {
        @Index(name = "idx_order_status_audit_order_created", columnList = "order_id, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "actor_sub", length = 160)
    private String actorSub;

    @Column(name = "actor_roles", length = 500)
    private String actorRoles;

    @Column(name = "actor_type", nullable = false, length = 40)
    private String actorType;

    @Column(name = "change_source", nullable = false, length = 40)
    private String changeSource;

    @Column(name = "note", length = 240)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
