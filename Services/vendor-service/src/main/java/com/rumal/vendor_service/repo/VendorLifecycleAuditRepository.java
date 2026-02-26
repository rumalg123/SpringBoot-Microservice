package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.VendorLifecycleAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorLifecycleAuditRepository extends JpaRepository<VendorLifecycleAudit, UUID> {
    List<VendorLifecycleAudit> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<VendorLifecycleAudit> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);
}

