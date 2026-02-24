package com.rumal.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_payouts", indexes = {
        @Index(name = "idx_payout_vendor_id", columnList = "vendor_id"),
        @Index(name = "idx_payout_status", columnList = "status"),
        @Index(name = "idx_payout_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VendorPayout {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFee;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "vendor_order_ids", nullable = false, length = 2000)
    private String vendorOrderIds;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private VendorBankAccount bankAccount;

    @Column(name = "bank_name_snapshot", nullable = false, length = 200)
    private String bankNameSnapshot;

    @Column(name = "account_number_snapshot", nullable = false, length = 100)
    private String accountNumberSnapshot;

    @Column(name = "account_holder_snapshot", nullable = false, length = 200)
    private String accountHolderSnapshot;

    @Column(name = "branch_code_snapshot", length = 50)
    private String branchCodeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status;

    @Column(name = "reference_number", length = 200)
    private String referenceNumber;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "completed_by", length = 120)
    private String completedBy;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
