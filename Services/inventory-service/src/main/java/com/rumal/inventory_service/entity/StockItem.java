package com.rumal.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stock_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stock_items_product_warehouse", columnNames = {"product_id", "warehouse_id"})
        },
        indexes = {
                @Index(name = "idx_stock_items_product_id", columnList = "product_id"),
                @Index(name = "idx_stock_items_warehouse_id", columnList = "warehouse_id"),
                @Index(name = "idx_stock_items_vendor_id", columnList = "vendor_id"),
                @Index(name = "idx_stock_items_sku", columnList = "sku"),
                @Index(name = "idx_stock_items_stock_status", columnList = "stock_status"),
                @Index(name = "idx_stock_items_vendor_product", columnList = "vendor_id, product_id"),
                @Index(name = "idx_stock_items_available_threshold", columnList = "quantity_available, low_stock_threshold")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(length = 80)
    private String sku;

    @Builder.Default
    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand = 0;

    @Builder.Default
    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved = 0;

    @Builder.Default
    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable = 0;

    @Builder.Default
    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 10;

    @Builder.Default
    @Column(nullable = false)
    private boolean backorderable = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 20)
    private StockStatus stockStatus = StockStatus.IN_STOCK;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public void recalculateAvailable() {
        this.quantityAvailable = this.quantityOnHand - this.quantityReserved;
    }

    public void recalculateStatus() {
        if (this.quantityAvailable <= 0 && this.backorderable) {
            this.stockStatus = StockStatus.BACKORDER;
        } else if (this.quantityAvailable <= 0) {
            this.stockStatus = StockStatus.OUT_OF_STOCK;
        } else if (this.quantityAvailable <= this.lowStockThreshold) {
            this.stockStatus = StockStatus.LOW_STOCK;
        } else {
            this.stockStatus = StockStatus.IN_STOCK;
        }
    }
}
