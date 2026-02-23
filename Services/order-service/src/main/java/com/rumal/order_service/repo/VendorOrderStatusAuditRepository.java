package com.rumal.order_service.repo;

import com.rumal.order_service.entity.VendorOrderStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorOrderStatusAuditRepository extends JpaRepository<VendorOrderStatusAudit, UUID> {
    List<VendorOrderStatusAudit> findByVendorOrderIdOrderByCreatedAtDesc(UUID vendorOrderId);
}
