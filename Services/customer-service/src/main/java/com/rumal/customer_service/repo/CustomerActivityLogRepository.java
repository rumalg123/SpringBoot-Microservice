package com.rumal.customer_service.repo;

import com.rumal.customer_service.entity.CustomerActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerActivityLogRepository extends JpaRepository<CustomerActivityLog, UUID> {
    Page<CustomerActivityLog> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
