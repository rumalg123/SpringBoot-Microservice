package com.rumal.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stock_reservations",
        indexes = {
                @Index(name = "idx_stock_reservations_order_id", columnList = "order_id"),
                @Index(name = "idx_stock_reservations_status", columnList = "status"),
                @Index(name = "idx_stock_reservations_expires_at", columnList = "expires_at"),
                @Index(name = "idx_stock_reservations_order_status", columnList = "order_id, status"),
                @Index(name = "idx_stock_reservations_stock_item_id", columnList = "stock_item_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
