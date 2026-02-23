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
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue
    private UUID id;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

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
}
