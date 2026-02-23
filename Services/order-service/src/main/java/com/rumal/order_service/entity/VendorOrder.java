package com.rumal.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_orders",
        indexes = {
                @Index(name = "idx_vendor_orders_vendor_id", columnList = "vendor_id"),
                @Index(name = "idx_vendor_orders_order_id", columnList = "order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "order_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal orderTotal;

    @OneToMany(mappedBy = "vendorOrder")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
