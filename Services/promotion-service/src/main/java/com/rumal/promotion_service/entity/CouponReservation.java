package com.rumal.promotion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "coupon_reservations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_coupon_reservations_request_key", columnNames = "request_key")
        },
        indexes = {
                @Index(name = "idx_coupon_reservations_coupon", columnList = "coupon_code_id"),
                @Index(name = "idx_coupon_reservations_customer", columnList = "customer_id"),
                @Index(name = "idx_coupon_reservations_status", columnList = "status"),
                @Index(name = "idx_coupon_reservations_expires_at", columnList = "expires_at"),
                @Index(name = "idx_coupon_reservations_order_id", columnList = "order_id"),
                @Index(name = "idx_coupon_reservations_customer_status", columnList = "customer_id, status"),
                @Index(name = "idx_coupon_reservations_coupon_customer_status", columnList = "coupon_code_id, customer_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponReservation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_code_id", nullable = false)
    private CouponCode couponCode;

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "coupon_code_text", nullable = false, length = 64)
    private String couponCodeText;

    @Column(name = "request_key", length = 120, unique = true)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponReservationStatus status;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "reserved_discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedDiscountAmount;

    @Column(name = "quoted_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal quotedSubtotal;

    @Column(name = "quoted_grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal quotedGrandTotal;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "committed_at")
    private Instant committedAt;

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
