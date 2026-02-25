package com.rumal.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_bank_accounts", indexes = {
        @Index(name = "idx_bank_vendor_id", columnList = "vendor_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VendorBankAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "bank_name", nullable = false, length = 200)
    private String bankName;

    @Column(name = "branch_name", length = 200)
    private String branchName;

    @Column(name = "branch_code", length = 50)
    private String branchCode;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 200)
    private String accountHolderName;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
