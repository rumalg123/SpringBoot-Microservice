package com.rumal.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_orders",
        indexes = {
                @Index(name = "idx_vendor_orders_vendor_id", columnList = "vendor_id"),
                @Index(name = "idx_vendor_orders_order_id", columnList = "order_id"),
                @Index(name = "idx_vendor_orders_vendor_status_created", columnList = "vendor_id, status, created_at DESC")
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

    @Version
    private Long version;

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

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Column(name = "carrier_code", length = 50)
    private String carrierCode;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "shipping_amount", precision = 19, scale = 2)
    private BigDecimal shippingAmount;

    @Column(name = "platform_fee", precision = 19, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "payout_amount", precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "refund_amount", precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refunded_amount", precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "refunded_quantity")
    private Integer refundedQuantity;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refund_initiated_at")
    private Instant refundInitiatedAt;

    @Column(name = "refund_completed_at")
    private Instant refundCompletedAt;

    @OneToMany(mappedBy = "vendorOrder")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
