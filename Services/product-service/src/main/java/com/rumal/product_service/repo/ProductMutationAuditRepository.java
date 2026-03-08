package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductMutationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductMutationAuditRepository extends JpaRepository<ProductMutationAudit, UUID> {

    Page<ProductMutationAudit> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    boolean existsBySourceEventId(UUID sourceEventId);
}
