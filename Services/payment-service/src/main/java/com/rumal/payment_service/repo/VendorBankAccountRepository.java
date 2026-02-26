package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.VendorBankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorBankAccountRepository extends JpaRepository<VendorBankAccount, UUID> {

    Page<VendorBankAccount> findByVendorIdAndActiveTrue(UUID vendorId, Pageable pageable);

    List<VendorBankAccount> findByVendorIdAndActiveTrue(UUID vendorId);

    Optional<VendorBankAccount> findByVendorIdAndPrimaryTrueAndActiveTrue(UUID vendorId);

    @Modifying
    @Query("UPDATE VendorBankAccount b SET b.primary = false WHERE b.vendorId = :vendorId AND b.primary = true")
    int unsetPrimaryForVendor(@Param("vendorId") UUID vendorId);
}
