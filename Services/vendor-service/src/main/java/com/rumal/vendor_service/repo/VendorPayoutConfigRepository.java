package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.VendorPayoutConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendorPayoutConfigRepository extends JpaRepository<VendorPayoutConfig, UUID> {
    Optional<VendorPayoutConfig> findByVendorId(UUID vendorId);
}
