package com.rumal.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stock_movements",
        indexes = {
                @Index(name = "idx_stock_movements_stock_item_id", columnList = "stock_item_id"),
                @Index(name = "idx_stock_movements_product_id", columnList = "product_id"),
                @Index(name = "idx_stock_movements_movement_type", columnList = "movement_type"),
                @Index(name = "idx_stock_movements_reference", columnList = "reference_type, reference_id"),
                @Index(name = "idx_stock_movements_created_at", columnList = "created_at"),
                @Index(name = "idx_stock_movements_actor", columnList = "actor_type, actor_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "actor_type", length = 30)
    private String actorType;

    @Column(name = "actor_id", length = 200)
    private String actorId;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
