package com.rumal.vendor_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
        name = "vendor_payout_configs",
        indexes = {
                @Index(name = "idx_vendor_payout_configs_vendor", columnList = "vendor_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorPayoutConfig {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private Vendor vendor;

    @Column(name = "payout_currency", nullable = false, length = 3)
    private String payoutCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_schedule", nullable = false, length = 20)
    @Builder.Default
    private PayoutSchedule payoutSchedule = PayoutSchedule.MONTHLY;

    @Column(name = "payout_minimum", precision = 19, scale = 2)
    private BigDecimal payoutMinimum;

    @Column(name = "bank_account_holder", length = 180)
    private String bankAccountHolder;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_routing_code", length = 60)
    private String bankRoutingCode;

    @Column(name = "bank_account_number_masked", length = 60)
    private String bankAccountNumberMasked;

    @Column(name = "tax_id", length = 60)
    private String taxId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
