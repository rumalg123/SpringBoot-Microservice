package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.PayoutStatus;
import com.rumal.payment_service.entity.VendorPayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorPayoutRepository extends JpaRepository<VendorPayout, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM VendorPayout p WHERE p.id = :id")
    Optional<VendorPayout> findByIdForUpdate(UUID id);

    @Query("SELECT p FROM VendorPayout p WHERE (:vendorId IS NULL OR p.vendorId = :vendorId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<VendorPayout> findFiltered(UUID vendorId, PayoutStatus status, Pageable pageable);

    Page<VendorPayout> findByVendorId(UUID vendorId, Pageable pageable);

    // H-07: Find active (non-cancelled) payouts for a vendor to check for duplicate vendor order references
    @Query("SELECT p FROM VendorPayout p WHERE p.vendorId = :vendorId AND p.status <> com.rumal.payment_service.entity.PayoutStatus.CANCELLED")
    List<VendorPayout> findActiveByVendorId(UUID vendorId);
}
