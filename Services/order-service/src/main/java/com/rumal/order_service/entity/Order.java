package com.rumal.order_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 120)
    private String item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "order_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal orderTotal;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "line_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineDiscountTotal;

    @Column(name = "cart_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal cartDiscountTotal;

    @Column(name = "shipping_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingAmount;

    @Column(name = "shipping_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingDiscountTotal;

    @Column(name = "total_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "coupon_reservation_id")
    private UUID couponReservationId;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "payment_id", length = 120)
    private String paymentId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_gateway_ref", length = 200)
    private String paymentGatewayRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "refund_amount", precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refund_initiated_at")
    private Instant refundInitiatedAt;

    @Column(name = "refund_completed_at")
    private Instant refundCompletedAt;

    @Column(name = "shipping_address_id", nullable = false)
    private UUID shippingAddressId;

    @Column(name = "billing_address_id", nullable = false)
    private UUID billingAddressId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "shipping_label", length = 50)),
            @AttributeOverride(name = "recipientName", column = @Column(name = "shipping_recipient_name", nullable = false, length = 120)),
            @AttributeOverride(name = "phone", column = @Column(name = "shipping_phone", nullable = false, length = 32)),
            @AttributeOverride(name = "line1", column = @Column(name = "shipping_line_1", nullable = false, length = 180)),
            @AttributeOverride(name = "line2", column = @Column(name = "shipping_line_2", length = 180)),
            @AttributeOverride(name = "city", column = @Column(name = "shipping_city", nullable = false, length = 80)),
            @AttributeOverride(name = "state", column = @Column(name = "shipping_state", nullable = false, length = 80)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code", nullable = false, length = 30)),
            @AttributeOverride(name = "countryCode", column = @Column(name = "shipping_country_code", nullable = false, length = 2))
    })
    private OrderAddressSnapshot shippingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "billing_label", length = 50)),
            @AttributeOverride(name = "recipientName", column = @Column(name = "billing_recipient_name", nullable = false, length = 120)),
            @AttributeOverride(name = "phone", column = @Column(name = "billing_phone", nullable = false, length = 32)),
            @AttributeOverride(name = "line1", column = @Column(name = "billing_line_1", nullable = false, length = 180)),
            @AttributeOverride(name = "line2", column = @Column(name = "billing_line_2", length = 180)),
            @AttributeOverride(name = "city", column = @Column(name = "billing_city", nullable = false, length = 80)),
            @AttributeOverride(name = "state", column = @Column(name = "billing_state", nullable = false, length = 80)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "billing_postal_code", nullable = false, length = 30)),
            @AttributeOverride(name = "countryCode", column = @Column(name = "billing_country_code", nullable = false, length = 2))
    })
    private OrderAddressSnapshot billingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VendorOrder> vendorOrders = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }
}
