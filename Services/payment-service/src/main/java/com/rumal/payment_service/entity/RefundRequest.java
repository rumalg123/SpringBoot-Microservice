package com.rumal.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_requests", indexes = {
        @Index(name = "idx_refund_payment_id", columnList = "payment_id"),
        @Index(name = "idx_refund_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_vendor_id", columnList = "vendor_id"),
        @Index(name = "idx_refund_status", columnList = "status"),
        @Index(name = "idx_refund_vendor_deadline", columnList = "vendor_response_deadline")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefundRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "vendor_order_id", nullable = false)
    private UUID vendorOrderId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_keycloak_id", nullable = false, length = 120)
    private String customerKeycloakId;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "customer_reason", nullable = false, length = 1000)
    private String customerReason;

    @Column(name = "vendor_response_note", length = 1000)
    private String vendorResponseNote;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefundStatus status;

    @Column(name = "vendor_responded_by", length = 120)
    private String vendorRespondedBy;

    @Column(name = "admin_finalized_by", length = 120)
    private String adminFinalizedBy;

    @Column(name = "vendor_response_deadline")
    private Instant vendorResponseDeadline;

    @Column(name = "payhere_refund_ref", length = 200)
    private String payhereRefundRef;

    @Column(name = "vendor_responded_at")
    private Instant vendorRespondedAt;

    @Column(name = "admin_finalized_at")
    private Instant adminFinalizedAt;

    @Column(name = "refund_completed_at")
    private Instant refundCompletedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
