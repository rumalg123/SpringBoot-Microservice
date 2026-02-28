package com.rumal.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id"),
        @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_payhere_payment_id", columnList = "payhere_payment_id"),
        @Index(name = "idx_payments_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_keycloak_id", nullable = false, length = 120)
    private String customerKeycloakId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "payhere_payment_id", length = 120)
    private String payherePaymentId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "card_holder_name", length = 200)
    private String cardHolderName;

    @Column(name = "card_no_masked", length = 30)
    private String cardNoMasked;

    @Column(name = "card_expiry", length = 10)
    private String cardExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "payhere_status_code")
    private Integer payhereStatusCode;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "items_description", nullable = false, length = 500)
    private String itemsDescription;

    @Column(name = "customer_first_name", nullable = false, length = 100)
    private String customerFirstName;

    @Column(name = "customer_last_name", nullable = false, length = 100)
    private String customerLastName;

    @Column(name = "customer_email", nullable = false, length = 200)
    private String customerEmail;

    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(name = "customer_address", length = 300)
    private String customerAddress;

    @Column(name = "customer_city", length = 80)
    private String customerCity;

    @Column(name = "customer_country", length = 2)
    private String customerCountry;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // C-04: Hash of webhook params for deduplication (merchant_id+order_id+status_code+amount)
    @Column(name = "webhook_idempotency_hash", length = 64)
    private String webhookIdempotencyHash;

    @Builder.Default
    @Column(name = "order_sync_pending", nullable = false)
    private boolean orderSyncPending = false;

    @Builder.Default
    @Column(name = "order_sync_retry_count", nullable = false)
    private int orderSyncRetryCount = 0;

    @Builder.Default
    @Column(name = "order_sync_max_retries", nullable = false)
    private int orderSyncMaxRetries = 10;

    // H-04: Flag for permanently failed order syncs requiring manual reconciliation
    @Builder.Default
    @Column(name = "order_sync_failed", nullable = false)
    private boolean orderSyncFailed = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
